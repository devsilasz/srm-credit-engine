package br.com.srm.creditengine.application;

import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import br.com.srm.creditengine.domain.pricing.PricingEngine;
import br.com.srm.creditengine.domain.pricing.TermCalculator;
import br.com.srm.creditengine.infrastructure.persistence.FxRate;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/** Precifica sem persistir. A liquidação usa este mesmo cálculo, no servidor. */
@Service
public class QuoteService {

  private final PricingEngine engine;
  private final FxRateService fxRates;
  private final Clock clock;
  private final Timer pricingTimer;

  public QuoteService(
      PricingEngine engine, FxRateService fxRates, Clock clock, MeterRegistry meters) {
    this.engine = engine;
    this.fxRates = fxRates;
    this.clock = clock;
    // Mede só o motor, sem banco: os percentis são probabilidades, não dinheiro.
    this.pricingTimer =
        Timer.builder("credit_engine_pricing_duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meters);
  }

  public Quote quote(
      ReceivableType type, BigDecimal faceValue, LocalDate dueDate, PaymentCurrency currency) {
    int termMonths = TermCalculator.termMonths(LocalDate.now(clock), dueDate);
    FxRate fxRate = currency.requiresFx() ? fxRates.requireCurrent(currency.fxPair()) : null;
    BigDecimal rate = fxRate == null ? null : fxRate.getRate();
    return new Quote(
        pricingTimer.record(() -> engine.price(type, faceValue, termMonths, currency, rate)),
        fxRate);
  }
}
