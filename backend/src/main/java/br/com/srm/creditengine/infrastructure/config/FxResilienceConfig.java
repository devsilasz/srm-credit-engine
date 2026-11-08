package br.com.srm.creditengine.infrastructure.config;

import br.com.srm.creditengine.application.FxRateService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FxResilienceConfig {

  @Bean
  CircuitBreakerRegistry circuitBreakerRegistry() {
    return CircuitBreakerRegistry.of(
        CircuitBreakerConfig.custom()
            .slidingWindowSize(6)
            .minimumNumberOfCalls(6)
            .failureRateThreshold(50)
            .waitDurationInOpenState(FxRateService.RETRY_AFTER)
            .permittedNumberOfCallsInHalfOpenState(1)
            .build());
  }

  @Bean
  CircuitBreaker fxProviderCircuitBreaker(CircuitBreakerRegistry registry) {
    return registry.circuitBreaker("fx-provider");
  }

  /** Publica {@code resilience4j_circuitbreaker_state} no Prometheus. */
  @Bean
  MeterBinder circuitBreakerMetrics(CircuitBreakerRegistry registry) {
    return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry);
  }

  /** Circuito aberto não é retentado: a resposta precisa sair na hora, sem chamar o provedor. */
  @Bean
  Retry fxProviderRetry() {
    return Retry.of(
        "fx-provider",
        RetryConfig.custom()
            .maxAttempts(3)
            .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofMillis(200), 2))
            .ignoreExceptions(CallNotPermittedException.class)
            .build());
  }

  @Bean
  TimeLimiter fxProviderTimeLimiter(FxProperties properties) {
    return TimeLimiter.of(
        TimeLimiterConfig.custom()
            .timeoutDuration(properties.providerTimeout())
            .cancelRunningFuture(true)
            .build());
  }
}
