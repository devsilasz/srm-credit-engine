package br.com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.srm.creditengine.AbstractIT;
import br.com.srm.creditengine.TestData;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FxRateApiIT extends AbstractIT {

  private JsonNode current() {
    return rest.getForObject("/api/fx-rates/current?pair=USD-BRL", JsonNode.class);
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  @Test
  @DisplayName("AC-FX-01 a subida grava a taxa do provedor mock")
  void startupRefreshStoresProviderRate() {
    assertThat(
            jdbc.queryForObject(
                "SELECT rate FROM fx_rate WHERE source = 'PROVIDER' ORDER BY id LIMIT 1",
                String.class))
        .isEqualTo("5.43210000");
  }

  @Test
  @DisplayName("AC-FX-02 POST cria uma linha MANUAL sem alterar as existentes")
  void manualRateIsANewRow() {
    long before = jdbc.queryForObject("SELECT count(*) FROM fx_rate", Long.class);
    Map<String, Object> body =
        Map.of("pair", "USD-BRL", "rate", "5.50000000", "validFrom", now().toString());

    var response = rest.postForEntity("/api/fx-rates", body, JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("source").asText()).isEqualTo("MANUAL");
    assertThat(response.getBody().get("rate").asText()).isEqualTo("5.50000000");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM fx_rate", Long.class))
        .isEqualTo(before + 1);
  }

  @Test
  @DisplayName("AC-FX-03 entre duas taxas válidas, vale a de vigência mais recente")
  void currentIsLatestValidFrom() {
    Instant t2 = now();
    TestData.fxRate(jdbc, "5.10", t2.minus(Duration.ofSeconds(30)));
    long latest = TestData.fxRate(jdbc, "5.20", t2);

    assertThat(current().get("id").asLong()).isEqualTo(latest);
    assertThat(current().get("rate").asText()).isEqualTo("5.20000000");
  }

  @Test
  @DisplayName("AC-FX-04 taxa com vigência no futuro ainda não vale")
  void futureRateIsNotCurrent() {
    long future = TestData.fxRate(jdbc, "9.99", now().plus(Duration.ofMinutes(30)));
    TestData.fxRate(jdbc, "5.30", now());

    assertThat(current().get("id").asLong()).isNotEqualTo(future);
    assertThat(current().get("rate").asText()).isEqualTo("5.30000000");
  }

  @Test
  @DisplayName("AC-FX-05 taxa menor ou igual a zero é 422")
  void refusesNonPositiveRate() {
    Map<String, Object> body =
        Map.of("pair", "USD-BRL", "rate", "0", "validFrom", now().toString());

    var response = rest.postForEntity("/api/fx-rates", body, JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody().get("code").asText()).isEqualTo("INVALID_FX_RATE");
  }

  @Test
  @DisplayName("AC-FX-05 taxa com mais de 8 casas é 400")
  void refusesRateWithTooManyDecimals() {
    Map<String, Object> body =
        Map.of("pair", "USD-BRL", "rate", "5.123456789", "validFrom", now().toString());

    assertThat(rest.postForEntity("/api/fx-rates", body, JsonNode.class).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void refreshStoresANewProviderRate() {
    var response = rest.postForEntity("/api/fx-rates/refresh", null, JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("source").asText()).isEqualTo("PROVIDER");
    assertThat(current().get("id").asLong()).isEqualTo(response.getBody().get("id").asLong());
  }

  @Test
  void staleRateIsNotCurrent() {
    TestData.fxRate(jdbc, "5.40", now());
    clock.advance(Duration.ofDays(100));

    var response = rest.getForEntity("/api/fx-rates/current?pair=USD-BRL", JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().get("code").asText()).isEqualTo("FX_RATE_NOT_FOUND");
  }
}
