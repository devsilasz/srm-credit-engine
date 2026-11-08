package br.com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.srm.creditengine.AbstractIT;
import br.com.srm.creditengine.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class QuoteApiIT extends AbstractIT {

  private ResponseEntity<JsonNode> quote(
      String faceValue, LocalDate dueDate, String type, String currency) {
    Map<String, Object> body =
        Map.of(
            "faceValue", faceValue,
            "dueDate", dueDate.toString(),
            "type", type,
            "paymentCurrency", currency);
    return rest.postForEntity("/api/pricing/quote", body, JsonNode.class);
  }

  @Test
  @DisplayName("AC-PRC-01 C1 pela API, sem perda de precisão na serialização")
  void goldenCaseC1() {
    JsonNode body =
        quote("100000.00", today().plusMonths(3), "DUPLICATA_MERCANTIL", "BRL").getBody();

    assertThat(body.get("presentValue").asText()).isEqualTo("92859.94");
    assertThat(body.get("discount").asText()).isEqualTo("7140.06");
    assertThat(body.has("fxRate")).isFalse();
  }

  @Test
  @DisplayName("AC-PRC-03 C3 pela API, com a taxa e o id dela na resposta")
  void goldenCaseC3() {
    long fxRateId = TestData.fxRate(jdbc, "5.4321", clock.instant());

    JsonNode body =
        quote("100000.00", today().plusMonths(3), "DUPLICATA_MERCANTIL", "USD").getBody();

    assertThat(body.get("presentValue").asText()).isEqualTo("17094.67");
    assertThat(body.get("presentValueBrl").asText()).isEqualTo("92859.94");
    assertThat(body.get("discount").asText()).isEqualTo("7140.06");
    assertThat(body.get("fxRate").asText()).isEqualTo("5.43210000");
    assertThat(body.get("fxRateId").asLong()).isEqualTo(fxRateId);
    assertThat(body.get("fxRateValidFrom").isTextual()).isTrue();
  }

  @Test
  @DisplayName("AC-PRC-08 vencimento em hoje + 3 meses resulta em termMonths = 3")
  void exactMonths() {
    JsonNode body =
        quote("100000.00", today().plusMonths(3), "DUPLICATA_MERCANTIL", "BRL").getBody();

    assertThat(body.get("termMonths").asInt()).isEqualTo(3);
  }

  @Test
  @DisplayName("AC-PRC-09 vencimento em hoje + 2 meses e 1 dia arredonda para 3 meses")
  void partialMonthRoundsUp() {
    JsonNode body =
        quote("100000.00", today().plusMonths(2).plusDays(1), "DUPLICATA_MERCANTIL", "BRL")
            .getBody();

    assertThat(body.get("termMonths").asInt()).isEqualTo(3);
  }

  @Test
  @DisplayName("AC-PRC-10 vencimento em hoje é 422")
  void dueDateNotInFuture() {
    ResponseEntity<JsonNode> response = quote("100000.00", today(), "DUPLICATA_MERCANTIL", "BRL");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody().get("code").asText()).isEqualTo("DUE_DATE_NOT_IN_FUTURE");
  }

  @Test
  @DisplayName("AC-PRC-16 prazo acima de 360 meses é 422")
  void dueDateTooFar() {
    ResponseEntity<JsonNode> response =
        quote("100000.00", today().plusMonths(361), "DUPLICATA_MERCANTIL", "BRL");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody().get("code").asText()).isEqualTo("DUE_DATE_TOO_FAR");
  }

  @Test
  @DisplayName("AC-PRC-17 PV que arredonda para zero é 422")
  void presentValueTooSmall() {
    TestData.fxRate(jdbc, "5.4321", clock.instant());

    ResponseEntity<JsonNode> response =
        quote("0.01", today().plusMonths(1), "DUPLICATA_MERCANTIL", "USD");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody().get("code").asText()).isEqualTo("PRESENT_VALUE_TOO_SMALL");
  }

  @Test
  @DisplayName("AC-PRC-13 valores são strings JSON com escala fixa")
  void valuesAreStrings() {
    JsonNode body = quote("25000.00", today().plusMonths(2), "CHEQUE_PRE_DATADO", "BRL").getBody();

    assertThat(body.get("presentValue").isTextual()).isTrue();
    assertThat(body.get("presentValue").asText()).isEqualTo("23337.77");
    assertThat(body.get("discount").isTextual()).isTrue();
    assertThat(body.get("discount").asText()).isEqualTo("1662.23");
  }

  @Test
  @DisplayName("AC-PRC-15 a resposta explica o cálculo")
  void responseExplainsCalculation() {
    JsonNode body = quote("25000.00", today().plusMonths(2), "CHEQUE_PRE_DATADO", "BRL").getBody();

    assertThat(body.get("baseRate").asText()).isEqualTo("0.010000");
    assertThat(body.get("spread").asText()).isEqualTo("0.025000");
    assertThat(body.get("termMonths").asInt()).isEqualTo(2);
  }

  @Test
  void quoteInUsdWithStaleRateIs503() {
    TestData.fxRate(jdbc, "5.4321", clock.instant());
    clock.advance(Duration.ofDays(100));

    ResponseEntity<JsonNode> response =
        quote("100000.00", today().plusMonths(3), "DUPLICATA_MERCANTIL", "USD");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody().get("code").asText()).isEqualTo("FX_RATE_UNAVAILABLE");
  }

  @Test
  void quoteDoesNotPersistAnything() {
    long before = jdbc.queryForObject("SELECT count(*) FROM settlement", Long.class);

    quote("100000.00", today().plusMonths(3), "DUPLICATA_MERCANTIL", "BRL");

    assertThat(jdbc.queryForObject("SELECT count(*) FROM settlement", Long.class))
        .isEqualTo(before);
  }
}
