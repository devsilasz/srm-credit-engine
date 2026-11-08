package br.com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.srm.creditengine.AbstractIT;
import br.com.srm.creditengine.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIT extends AbstractIT {

  private static final String DUPLICATA = "DUPLICATA_MERCANTIL";

  @Autowired ObjectMapper objectMapper;

  /** Lê o valor como o Prometheus leria. Zero quando a série ainda não existe. */
  private String settlementCounter(String currency, String type, String outcome) {
    String series =
        "credit_engine_settlements_total{currency=\"%s\",outcome=\"%s\",type=\"%s\"} "
            .formatted(currency, outcome, type);
    String metrics = rest.getForObject("/actuator/prometheus", String.class);
    Matcher value = Pattern.compile(Pattern.quote(series) + "(\\S+)").matcher(metrics);
    return value.find() ? value.group(1) : "0.0";
  }

  private static String plusOne(String counter) {
    return new BigDecimal(counter).add(BigDecimal.ONE).toPlainString();
  }

  private void assertIncremented(String before, String currency, String type, String outcome) {
    assertThat(new BigDecimal(settlementCounter(currency, type, outcome)))
        .isEqualByComparingTo(plusOne(before));
  }

  @Test
  @DisplayName("AC-OPS-02 liquidação concluída incrementa o contador com outcome=success")
  void successIncrementsCounter() {
    String before = settlementCounter("BRL", DUPLICATA, "success");

    settle(UUID.randomUUID().toString(), openReceivable(), "BRL");

    assertIncremented(before, "BRL", DUPLICATA, "success");
  }

  @Test
  @DisplayName("AC-OPS-03 replay e recusas 409, 422 e 503 entram no contador com o seu outcome")
  void rejectionsIncrementCounterWithTheirOutcome() {
    long settled = openReceivable();
    String key = UUID.randomUUID().toString();
    settle(key, settled, "BRL");

    String replay = settlementCounter("BRL", DUPLICATA, "replay");
    settle(key, settled, "BRL");
    assertIncremented(replay, "BRL", DUPLICATA, "replay");

    String conflict = settlementCounter("BRL", DUPLICATA, "receivable_already_settled");
    settle(UUID.randomUUID().toString(), settled, "BRL");
    assertIncremented(conflict, "BRL", DUPLICATA, "receivable_already_settled");

    String reused = settlementCounter("BRL", "UNKNOWN", "idempotency_key_reused");
    settle(key, openReceivable(), "BRL");
    assertIncremented(reused, "BRL", "UNKNOWN", "idempotency_key_reused");

    String notFound = settlementCounter("BRL", "UNKNOWN", "receivable_not_found");
    settle(UUID.randomUUID().toString(), 999999, "BRL");
    assertIncremented(notFound, "BRL", "UNKNOWN", "receivable_not_found");

    long stale = openReceivable(TestData.assignor(jdbc), today().plusMonths(12));
    clock.advance(Duration.ofDays(100));
    String unavailable = settlementCounter("USD", DUPLICATA, "fx_rate_unavailable");
    settle(UUID.randomUUID().toString(), stale, "USD");
    assertIncremented(unavailable, "USD", DUPLICATA, "fx_rate_unavailable");
  }

  @Test
  @DisplayName("AC-OPS-04 toda liquidação gera uma linha JSON com ids, outcome e duração")
  void logsStructuredLine(CapturedOutput output) throws Exception {
    long receivable = openReceivable();
    String key = UUID.randomUUID().toString();

    long settlementId = settle(key, receivable, "BRL").getBody().get("id").asLong();

    JsonNode line = settlementLogLine(output, key);
    assertThat(line.get("settlementId").asText()).isEqualTo(String.valueOf(settlementId));
    assertThat(line.get("receivableId").asText()).isEqualTo(String.valueOf(receivable));
    assertThat(line.get("idempotencyKey").asText()).isEqualTo(key);
    assertThat(line.get("outcome").asText()).isEqualTo("success");
    assertThat(line.get("durationMs").asLong()).isNotNegative();
  }

  @Test
  void refusedSettlementIsAlsoLogged(CapturedOutput output) throws Exception {
    String key = UUID.randomUUID().toString();

    settle(key, 999999, "BRL");

    JsonNode line = settlementLogLine(output, key);
    assertThat(line.get("outcome").asText()).isEqualTo("receivable_not_found");
    assertThat(line.has("settlementId")).isFalse();
  }

  private JsonNode settlementLogLine(CapturedOutput output, String key) throws Exception {
    String raw =
        output
            .getOut()
            .lines()
            .filter(l -> l.contains("Liquidação processada") && l.contains(key))
            .findFirst()
            .orElseThrow();
    return objectMapper.readTree(raw);
  }

  @Test
  @DisplayName("Timer do motor exposto com o p99 usado em AC-PERF-02")
  void pricingTimerIsExposed() {
    settle(UUID.randomUUID().toString(), openReceivable(), "BRL");

    assertThat(rest.getForObject("/actuator/prometheus", String.class))
        .contains("credit_engine_pricing_duration_seconds{quantile=\"0.99\"}")
        .contains("credit_engine_pricing_duration_seconds_count");
  }
}
