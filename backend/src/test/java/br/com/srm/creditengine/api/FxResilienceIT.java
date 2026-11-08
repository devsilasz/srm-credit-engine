package br.com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.srm.creditengine.AbstractIT;
import br.com.srm.creditengine.TestData;
import br.com.srm.creditengine.application.FxRateProvider;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class FxResilienceIT extends AbstractIT {

  @MockitoBean FxRateProvider provider;
  @Autowired CircuitBreaker fxProviderCircuitBreaker;

  @BeforeEach
  void closeCircuit() {
    reset(provider);
    fxProviderCircuitBreaker.reset();
  }

  private ResponseEntity<JsonNode> refresh() {
    return rest.postForEntity("/api/fx-rates/refresh", null, JsonNode.class);
  }

  private long rateCount() {
    return jdbc.queryForObject("SELECT count(*) FROM fx_rate", Long.class);
  }

  @Test
  @DisplayName("AC-FX-06 provedor lento: timeout por tentativa, 503 com Retry-After e nada gravado")
  void slowProviderTimesOut() {
    when(provider.fetchRate(anyString()))
        .thenAnswer(
            call -> {
              Thread.sleep(Duration.ofSeconds(10));
              return new BigDecimal("5.4321");
            });
    long before = rateCount();
    long start = System.nanoTime();

    ResponseEntity<JsonNode> response = refresh();

    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody().get("code").asText()).isEqualTo("FX_PROVIDER_UNAVAILABLE");
    assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
    assertThat(rateCount()).isEqualTo(before);
    verify(provider, times(3)).fetchRate("USD-BRL");
    // Três tentativas de 2 s, muito antes dos 10 s do provedor: o timeout de fato cortou a chamada.
    assertThat(elapsed).isLessThan(Duration.ofMillis(3 * 2500));
  }

  @Test
  @DisplayName("AC-FX-07 falhas seguidas abrem o circuito e o provedor deixa de ser chamado")
  void repeatedFailuresOpenCircuit() {
    doThrow(new IllegalStateException("provedor fora")).when(provider).fetchRate(anyString());

    refresh();
    refresh();
    assertThat(fxProviderCircuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    clearInvocations(provider);

    ResponseEntity<JsonNode> response = refresh();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
    verify(provider, never()).fetchRate(anyString());
    assertThat(rest.getForObject("/actuator/prometheus", String.class))
        .contains("resilience4j_circuitbreaker_state{name=\"fx-provider\",state=\"open\"} 1.0");
  }

  @Test
  @DisplayName("AC-FX-10 provedor fora, com taxa ainda válida: USD liquida sem chamar o provedor")
  void settlementDoesNotDependOnProvider() {
    doThrow(new IllegalStateException("provedor fora")).when(provider).fetchRate(anyString());
    long fxRateId = TestData.fxRate(jdbc, "5.4321", clock.instant());
    long receivable = openReceivable();

    ResponseEntity<JsonNode> response = settle(UUID.randomUUID().toString(), receivable, "USD");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("fxRateId").asLong()).isEqualTo(fxRateId);
    verify(provider, never()).fetchRate(anyString());
  }

  @Test
  void invalidRateFromProviderIsAProviderFailure() {
    when(provider.fetchRate(anyString())).thenReturn(BigDecimal.ZERO);
    long before = rateCount();

    assertThat(refresh().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(rateCount()).isEqualTo(before);
  }

  @Test
  void transientFailureIsRetried() {
    when(provider.fetchRate(anyString()))
        .thenThrow(new IllegalStateException("oscilação"))
        .thenReturn(new BigDecimal("5.4321"));

    assertThat(refresh().getStatusCode()).isEqualTo(HttpStatus.CREATED);
    verify(provider, times(2)).fetchRate("USD-BRL");
  }
}
