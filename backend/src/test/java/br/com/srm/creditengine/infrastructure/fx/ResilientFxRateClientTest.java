package br.com.srm.creditengine.infrastructure.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.srm.creditengine.application.FxRateProvider;
import br.com.srm.creditengine.application.FxRateService;
import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import br.com.srm.creditengine.infrastructure.persistence.FxRate;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** O mesmo arranjo da produção, com tempos curtos para o teste não esperar segundos. */
class ResilientFxRateClientTest {

  private final FxRateProvider provider = mock(FxRateProvider.class);
  private final CircuitBreaker circuit =
      CircuitBreaker.of(
          "teste",
          CircuitBreakerConfig.custom()
              .slidingWindowSize(3)
              .minimumNumberOfCalls(3)
              .failureRateThreshold(50)
              .waitDurationInOpenState(Duration.ofMinutes(1))
              .build());
  private final ResilientFxRateClient client =
      new ResilientFxRateClient(
          provider,
          circuit,
          Retry.of(
              "teste",
              RetryConfig.custom()
                  .maxAttempts(3)
                  .waitDuration(Duration.ofMillis(1))
                  .ignoreExceptions(CallNotPermittedException.class)
                  .build()),
          TimeLimiter.of(
              TimeLimiterConfig.custom()
                  .timeoutDuration(Duration.ofMillis(100))
                  .cancelRunningFuture(true)
                  .build()));

  private void assertProviderUnavailable() {
    assertThatThrownBy(() -> client.fetchRate("USD-BRL"))
        .isInstanceOfSatisfying(
            BusinessRuleException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.FX_PROVIDER_UNAVAILABLE));
  }

  @Test
  void returnsTheProviderRate() {
    when(provider.fetchRate("USD-BRL")).thenReturn(new BigDecimal("5.4321"));

    assertThat(client.fetchRate("USD-BRL")).isEqualByComparingTo("5.4321");
    verify(provider, times(1)).fetchRate("USD-BRL");
  }

  @Test
  void nullRateIsAProviderFailure() {
    when(provider.fetchRate("USD-BRL")).thenReturn(null);

    assertProviderUnavailable();
  }

  @Test
  void negativeRateIsAProviderFailure() {
    when(provider.fetchRate("USD-BRL")).thenReturn(new BigDecimal("-1"));

    assertProviderUnavailable();
  }

  @Test
  void timeoutCutsTheCallAndRetries() {
    when(provider.fetchRate("USD-BRL"))
        .thenAnswer(
            call -> {
              Thread.sleep(Duration.ofSeconds(10));
              return BigDecimal.ONE;
            });

    assertProviderUnavailable();
    verify(provider, times(3)).fetchRate("USD-BRL");
  }

  @Test
  void openCircuitDoesNotCallTheProvider() {
    circuit.transitionToOpenState();

    assertProviderUnavailable();
    verify(provider, never()).fetchRate(anyString());
  }

  @Test
  void refreshJobKeepsTheApplicationAliveWhenTheProviderIsDown() {
    FxRateService fxRates = mock(FxRateService.class);
    when(fxRates.refresh())
        .thenThrow(new BusinessRuleException(ErrorCode.FX_PROVIDER_UNAVAILABLE, "fora"));

    new FxRateRefreshJob(fxRates).run(null);

    verify(fxRates).refresh();
  }

  @Test
  void refreshJobCallsTheServiceOnSchedule() {
    FxRateService fxRates = mock(FxRateService.class);
    when(fxRates.refresh()).thenReturn(mock(FxRate.class));

    new FxRateRefreshJob(fxRates).refresh();

    verify(fxRates).refresh();
  }
}
