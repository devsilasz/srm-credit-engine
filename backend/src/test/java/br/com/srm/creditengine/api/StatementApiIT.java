package br.com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.srm.creditengine.AbstractIT;
import br.com.srm.creditengine.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Cada teste cria um cedente novo e filtra por ele: as liquidações de outros testes não somem. */
class StatementApiIT extends AbstractIT {

  private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
  private static final Instant SOME_TIME = Instant.parse("2026-03-10T15:00:00Z");

  private ResponseEntity<JsonNode> statement(String query) {
    return rest.getForEntity("/api/settlements?" + query, JsonNode.class);
  }

  private List<Long> ids(JsonNode page) {
    List<Long> ids = new ArrayList<>();
    page.get("content").forEach(item -> ids.add(item.get("id").asLong()));
    return ids;
  }

  @Test
  @DisplayName("AC-STM-01 paginação server-side, ordenada por settledAt desc")
  void paginatesNewestFirst() {
    long assignor = TestData.assignor(jdbc);
    List<Long> created = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      created.add(TestData.settlement(jdbc, assignor, SOME_TIME.plus(Duration.ofMinutes(i))));
    }

    JsonNode page = statement("assignorId=" + assignor + "&page=0&size=10").getBody();

    assertThat(page.get("content")).hasSize(10);
    assertThat(page.get("page").asInt()).isZero();
    assertThat(page.get("size").asInt()).isEqualTo(10);
    assertThat(page.get("totalElements").asLong()).isEqualTo(30);
    assertThat(page.get("totalPages").asInt()).isEqualTo(3);
    assertThat(ids(page)).isEqualTo(created.reversed().subList(0, 10));

    JsonNode last = statement("assignorId=" + assignor + "&page=2&size=10").getBody();
    assertThat(ids(last)).isEqualTo(created.reversed().subList(20, 30));
  }

  @Test
  @DisplayName("AC-STM-02 filtro de moeda traz só aquela moeda")
  void filtersByCurrency() {
    long assignor = TestData.assignor(jdbc);
    TestData.settlement(jdbc, assignor, SOME_TIME);
    long usd = TestData.usdSettlement(jdbc, assignor, SOME_TIME);

    JsonNode page = statement("assignorId=" + assignor + "&currency=USD").getBody();

    assertThat(ids(page)).containsExactly(usd);
  }

  @Test
  @DisplayName("AC-STM-03 filtro de cedente traz só ele, com o nome em cada item")
  void filtersByAssignor() {
    long assignorA = TestData.assignor(jdbc);
    long assignorB = TestData.assignor(jdbc);
    long fromA = TestData.settlement(jdbc, assignorA, SOME_TIME);
    TestData.settlement(jdbc, assignorB, SOME_TIME);

    JsonNode page = statement("assignorId=" + assignorA).getBody();

    assertThat(ids(page)).containsExactly(fromA);
    assertThat(page.get("content").get(0).get("assignorName").asText()).startsWith("Cedente ");
  }

  @Test
  @DisplayName("AC-STM-04 período inclui os dois dias inteiros, no fuso de São Paulo")
  void periodIsInclusiveInBusinessTimezone() {
    long assignor = TestData.assignor(jdbc);
    LocalDate d1 = LocalDate.parse("2026-03-10");
    LocalDate d2 = LocalDate.parse("2026-03-11");
    Instant startOfD1 = d1.atStartOfDay(SAO_PAULO).toInstant();
    Instant startOfD3 = d2.plusDays(1).atStartOfDay(SAO_PAULO).toInstant();

    TestData.settlement(jdbc, assignor, startOfD1.minusMillis(1));
    long first = TestData.settlement(jdbc, assignor, startOfD1);
    long last = TestData.settlement(jdbc, assignor, startOfD3.minusMillis(1));
    TestData.settlement(jdbc, assignor, startOfD3);

    JsonNode page = statement("assignorId=" + assignor + "&from=" + d1 + "&to=" + d2).getBody();

    assertThat(ids(page)).containsExactly(last, first);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"from=2026-03-11&to=2026-03-10", "size=101", "size=0", "page=-1", "currency=EUR"})
  @DisplayName("AC-STM-05 filtro inválido é 400")
  void invalidFilterIs400(String query) {
    assertThat(statement(query).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "sort=amount;drop table settlement",
        "sort=id,desc",
        "sort=settledAt,sideways",
        "sort=settledAt,desc,asc"
      })
  @DisplayName("AC-STM-06 sort fora da lista branca é 400")
  void sortOutsideWhitelistIs400(String query) {
    ResponseEntity<JsonNode> response = statement(query);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("code").asText()).isEqualTo("INVALID_FILTER");
  }

  @Test
  void sortsByRequestedColumnAndDirection() {
    long assignor = TestData.assignor(jdbc);
    long older = TestData.settlement(jdbc, assignor, SOME_TIME);
    long newer = TestData.settlement(jdbc, assignor, SOME_TIME.plusSeconds(60));

    assertThat(ids(statement("assignorId=" + assignor + "&sort=settledAt,asc").getBody()))
        .containsExactly(older, newer);
    assertThat(ids(statement("assignorId=" + assignor + "&sort=faceValue").getBody()))
        .containsExactly(newer, older);
  }

  @Test
  @DisplayName("AC-STM-09 valores são strings com escala fixa e moeda explícita por item")
  void itemsCarryStringValuesAndCurrency() {
    long assignor = TestData.assignor(jdbc);
    TestData.usdSettlement(jdbc, assignor, SOME_TIME);

    JsonNode item = statement("assignorId=" + assignor).getBody().get("content").get(0);

    assertThat(item.get("faceValue").isTextual()).isTrue();
    assertThat(item.get("faceValue").asText()).isEqualTo("100000.00");
    assertThat(item.get("presentValue").asText()).isEqualTo("17094.67");
    assertThat(item.get("discount").asText()).isEqualTo("7140.06");
    assertThat(item.get("paymentCurrency").asText()).isEqualTo("USD");
    assertThat(item.get("fxRate").asText()).isEqualTo("5.43210000");
  }

  @Test
  void statementIncludesASettlementMadeThroughTheApi() {
    long assignor = TestData.assignor(jdbc);
    long receivable = openReceivable(assignor, today().plusMonths(3));
    long id = settle(UUID.randomUUID().toString(), receivable, "BRL").getBody().get("id").asLong();

    assertThat(ids(statement("assignorId=" + assignor).getBody())).containsExactly(id);
  }
}
