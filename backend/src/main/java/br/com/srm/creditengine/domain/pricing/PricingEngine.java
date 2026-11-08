package br.com.srm.creditengine.domain.pricing;

import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Calcula {@code PV = Face / (1 + base + spread)^n} e converte o PV arredondado, se preciso. */
public final class PricingEngine {

  private final Map<ReceivableType, PricingStrategy> strategies =
      new EnumMap<>(ReceivableType.class);
  private final BigDecimal baseRateMonthly;

  /** Falha na subida se algum tipo do enum ficar sem estratégia, em vez de falhar na liquidação. */
  public PricingEngine(List<PricingStrategy> strategies, BigDecimal baseRateMonthly) {
    strategies.forEach(strategy -> this.strategies.put(strategy.type(), strategy));
    for (ReceivableType type : ReceivableType.values()) {
      if (!this.strategies.containsKey(type)) {
        throw new IllegalStateException("Sem PricingStrategy para " + type);
      }
    }
    this.baseRateMonthly = baseRateMonthly;
  }

  /**
   * @param fxRate BRL por 1 unidade da moeda de pagamento; obrigatório só se a moeda exigir câmbio
   */
  public PricingResult price(
      ReceivableType type,
      BigDecimal faceValue,
      int termMonths,
      PaymentCurrency currency,
      BigDecimal fxRate) {
    PricingStrategy strategy = strategies.get(type);
    BigDecimal factor = strategy.discountFactor(baseRateMonthly, termMonths);
    BigDecimal presentValueBrl = MonetaryPolicy.divideToMoney(faceValue, factor);
    BigDecimal presentValue =
        currency.requiresFx()
            ? MonetaryPolicy.divideToMoney(presentValueBrl, fxRate)
            : presentValueBrl;

    if (presentValue.signum() == 0) {
      throw new BusinessRuleException(
          ErrorCode.PRESENT_VALUE_TOO_SMALL,
          "O valor presente arredondado é zero em " + currency + ": o valor de face é pequeno.");
    }

    return new PricingResult(
        faceValue,
        termMonths,
        baseRateMonthly.setScale(MonetaryPolicy.RATE_SCALE, MonetaryPolicy.ROUNDING),
        strategy.spreadMonthly().setScale(MonetaryPolicy.RATE_SCALE, MonetaryPolicy.ROUNDING),
        presentValueBrl,
        faceValue.subtract(presentValueBrl),
        currency,
        presentValue);
  }
}
