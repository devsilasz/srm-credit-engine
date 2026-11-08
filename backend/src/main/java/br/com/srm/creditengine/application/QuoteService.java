package br.com.srm.creditengine.application;

import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import br.com.srm.creditengine.domain.pricing.PricingEngine;
import br.com.srm.creditengine.domain.pricing.TermCalculator;
import br.com.srm.creditengine.infrastructure.persistence.FxRate;
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

  public QuoteService(PricingEngine engine, FxRateService fxRates, Clock clock) {
    this.engine = engine;
    this.fxRates = fxRates;
    this.clock = clock;
  }

  public Quote quote(
      ReceivableType type, BigDecimal faceValue, LocalDate dueDate, PaymentCurrency currency) {
    int termMonths = TermCalculator.termMonths(LocalDate.now(clock), dueDate);
    FxRate fxRate = currency.requiresFx() ? fxRates.requireCurrent(currency.fxPair()) : null;
    BigDecimal rate = fxRate == null ? null : fxRate.getRate();
    return new Quote(engine.price(type, faceValue, termMonths, currency, rate), fxRate);
  }
}
