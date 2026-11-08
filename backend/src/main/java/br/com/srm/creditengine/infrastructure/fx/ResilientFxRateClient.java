package br.com.srm.creditengine.infrastructure.fx;

import br.com.srm.creditengine.application.FxRateProvider;
import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Chama o provedor com timeout por tentativa, dentro do circuit breaker, dentro do retry. Cada
 * tentativa conta para o circuito, e o circuito aberto corta o retry.
 */
@Component
public class ResilientFxRateClient {

  private static final Logger log = LoggerFactory.getLogger(ResilientFxRateClient.class);

  private final FxRateProvider provider;
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;
  private final TimeLimiter timeLimiter;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public ResilientFxRateClient(
      FxRateProvider provider,
      CircuitBreaker fxProviderCircuitBreaker,
      Retry fxProviderRetry,
      TimeLimiter fxProviderTimeLimiter) {
    this.provider = provider;
    this.circuitBreaker = fxProviderCircuitBreaker;
    this.retry = fxProviderRetry;
    this.timeLimiter = fxProviderTimeLimiter;
  }

  public BigDecimal fetchRate(String pair) {
    Callable<BigDecimal> withTimeout =
        timeLimiter.decorateFutureSupplier(() -> executor.submit(() -> validRate(pair)));
    Callable<BigDecimal> call =
        Retry.decorateCallable(retry, circuitBreaker.decorateCallable(withTimeout));
    try {
      return call.call();
    } catch (Exception e) {
      log.warn("Provedor de câmbio indisponível para {}", pair, e);
      throw new BusinessRuleException(
          ErrorCode.FX_PROVIDER_UNAVAILABLE, "O provedor de câmbio está indisponível no momento.");
    }
  }

  /** Taxa inválida é falha do provedor: conta para o circuito e nunca chega ao banco. */
  private BigDecimal validRate(String pair) {
    BigDecimal rate = provider.fetchRate(pair);
    if (rate == null || rate.signum() <= 0) {
      throw new IllegalStateException("Provedor devolveu taxa inválida: " + rate);
    }
    return rate;
  }
}
