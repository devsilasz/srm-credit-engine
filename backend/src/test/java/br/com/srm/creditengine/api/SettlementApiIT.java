package br.com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.srm.creditengine.AbstractIT;
import br.com.srm.creditengine.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class SettlementApiIT extends AbstractIT {

  private static String key() {
    return UUID.randomUUID().toString();
  }

  @Test
  @DisplayName("AC-SET-01 liquidação nova: 201, Location, recebível SETTLED e 1 linha")
  void settlesOpenReceivable() {
    long receivable = openReceivable();

    ResponseEntity<JsonNode> response = settle(key(), receivable, "BRL");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long id = response.getBody().get("id").asLong();
    assertThat(response.getHeaders().getLocation()).hasPath("/api/settlements/" + id);
    assertThat(receivableStatus(receivable)).isEqualTo("SETTLED");
    assertThat(settlementCount(receivable)).isEqualTo(1);
    assertThat(rest.getForObject("/api/settlements/" + id, JsonNode.class))
        .isEqualTo(response.getBody());
  }

  @Test
  @DisplayName("AC-SET-02 o registro guarda tudo o que foi usado no cálculo")
  void recordCarriesEverythingUsed() {
    long fxRateId = TestData.fxRate(jdbc, "5.4321", clock.instant());
    long receivable = openReceivable();
    String key = key();

    settle(key, receivable, "USD");

    Map<String, Object> row =
        jdbc.queryForMap("SELECT * FROM settlement WHERE receivable_id = ?", receivable);
    assertThat(row.get("face_value").toString()).isEqualTo("100000.00");
    assertThat(row.get("present_value_brl").toString()).isEqualTo("92859.94");
    assertThat(row.get("present_value_payment").toString()).isEqualTo("17094.67");
    assertThat(row.get("discount").toString()).isEqualTo("7140.06");
    assertThat(row.get("base_rate_monthly").toString()).isEqualTo("0.010000");
    assertThat(row.get("spread_monthly").toString()).isEqualTo("0.015000");
    assertThat(row.get("term_months")).isEqualTo(3);
    assertThat(row.get("fx_rate_id")).isEqualTo(fxRateId);
    assertThat(row.get("fx_rate").toString()).isEqualTo("5.43210000");
    assertThat(row.get("idempotency_key").toString()).isEqualTo(key);
    assertThat(row.get("request_hash").toString()).hasSize(64);
    assertThat(row.get("settled_at")).isNotNull();
  }

  @Test
  @DisplayName("AC-SET-03 replay: 200, mesmo corpo, header de replay e nenhuma linha nova")
  void sameKeySamePayloadReplays() {
    long receivable = openReceivable();
    String key = key();
    ResponseEntity<JsonNode> first = settle(key, receivable, "BRL");

    ResponseEntity<JsonNode> second = settle(key, receivable, "BRL");

    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(second.getBody()).isEqualTo(first.getBody());
    assertThat(second.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
    assertThat(settlementCount(receivable)).isEqualTo(1);
  }

  @Test
  @DisplayName("AC-SET-04 mesma chave com outra moeda é 422")
  void sameKeyOtherCurrencyIsRefused() {
    TestData.fxRate(jdbc, "5.4321", clock.instant());
    long receivable = openReceivable();
    String key = key();
    settle(key, receivable, "BRL");

    ResponseEntity<JsonNode> response = settle(key, receivable, "USD");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody().get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    assertThat(settlementCount(receivable)).isEqualTo(1);
  }

  @Test
  @DisplayName("AC-SET-04 mesma chave com outro recebível é 422 e nada é gravado")
  void sameKeyOtherReceivableIsRefused() {
    String key = key();
    settle(key, openReceivable(), "BRL");
    long other = openReceivable();

    ResponseEntity<JsonNode> response = settle(key, other, "BRL");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody().get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    assertThat(settlementCount(other)).isZero();
    assertThat(receivableStatus(other)).isEqualTo("OPEN");
  }

  @Test
  @DisplayName("AC-SET-05 sem Idempotency-Key é 400")
  void missingKeyIsRefused() {
    long receivable = openReceivable();

    ResponseEntity<JsonNode> response = settle(null, receivable, "BRL");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
    assertThat(settlementCount(receivable)).isZero();
  }

  @Test
  @DisplayName("AC-SET-05 Idempotency-Key fora do formato UUID é 400")
  void malformedKeyIsRefused() {
    long receivable = openReceivable();

    for (String key : new String[] {"abc", "1-1-1-1-1"}) {
      ResponseEntity<JsonNode> response = settle(key, receivable, "BRL");

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody().get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
    }
  }

  @Test
  @DisplayName("AC-SET-06 recebível já liquidado, com chave nova, é 409")
  void alreadySettledIsConflict() {
    long receivable = openReceivable();
    settle(key(), receivable, "BRL");

    ResponseEntity<JsonNode> response = settle(key(), receivable, "BRL");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().get("code").asText()).isEqualTo("RECEIVABLE_ALREADY_SETTLED");
    assertThat(settlementCount(receivable)).isEqualTo(1);
  }

  @Test
  @DisplayName("AC-SET-12 recebível inexistente é 404")
  void unknownReceivableIs404() {
    ResponseEntity<JsonNode> response = settle(key(), 999999, "BRL");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().get("code").asText()).isEqualTo("RECEIVABLE_NOT_FOUND");
  }

  @Test
  void unknownSettlementIs404() {
    ResponseEntity<JsonNode> response =
        rest.getForEntity("/api/settlements/999999", JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().get("code").asText()).isEqualTo("SETTLEMENT_NOT_FOUND");
  }

  @Test
  @DisplayName("AC-SET-13 valor enviado pelo cliente é ignorado e recalculado no servidor")
  void clientValuesAreIgnored() {
    long receivable = openReceivable();
    HttpHeaders headers = new HttpHeaders();
    headers.set("Idempotency-Key", key());
    Map<String, Object> body =
        Map.of("receivableId", receivable, "paymentCurrency", "BRL", "presentValue", "1.00");

    ResponseEntity<JsonNode> response =
        rest.postForEntity("/api/settlements", new HttpEntity<>(body, headers), JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("presentValue").asText()).isEqualTo("92859.94");
  }

  @Test
  @DisplayName("AC-SEC-02 receivableId com tentativa de SQL injection é 400")
  void sqlInjectionAttemptIsRefused() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Idempotency-Key", key());
    headers.setContentType(MediaType.APPLICATION_JSON);
    String body = "{\"receivableId\":\"1 OR 1=1\",\"paymentCurrency\":\"BRL\"}";

    ResponseEntity<JsonNode> response =
        rest.postForEntity("/api/settlements", new HttpEntity<>(body, headers), JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void overdueReceivableIsRefusedAtSettlement() {
    long receivable = openReceivable(TestData.assignor(jdbc), today().plusDays(1));
    clock.advance(Duration.ofDays(2));

    ResponseEntity<JsonNode> response = settle(key(), receivable, "BRL");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody().get("code").asText()).isEqualTo("DUE_DATE_NOT_IN_FUTURE");
    assertThat(receivableStatus(receivable)).isEqualTo("OPEN");
  }

  @Test
  @DisplayName("AC-FX-08 taxa vencida: USD é 503, nada gravado, e o recebível segue OPEN")
  void staleRateRefusesUsd() {
    TestData.fxRate(jdbc, "5.4321", clock.instant());
    long receivable = openReceivable(TestData.assignor(jdbc), today().plusMonths(12));
    clock.advance(Duration.ofDays(100));

    ResponseEntity<JsonNode> response = settle(key(), receivable, "USD");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody().get("code").asText()).isEqualTo("FX_RATE_UNAVAILABLE");
    assertThat(settlementCount(receivable)).isZero();
    assertThat(receivableStatus(receivable)).isEqualTo("OPEN");
  }

  @Test
  @DisplayName("AC-FX-09 taxa vencida não afeta a liquidação em BRL")
  void staleRateDoesNotAffectBrl() {
    long receivable = openReceivable(TestData.assignor(jdbc), today().plusMonths(12));
    clock.advance(Duration.ofDays(100));

    ResponseEntity<JsonNode> response = settle(key(), receivable, "BRL");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(settlementCount(receivable)).isEqualTo(1);
  }
}
