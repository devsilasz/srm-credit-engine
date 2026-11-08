package br.com.srm.creditengine.domain.pricing;

import br.com.srm.creditengine.domain.model.ReceivableType;
import java.math.BigDecimal;

/** Regra de risco de um tipo de recebível. Uma classe nova entra sozinha no motor (OCP). */
public interface PricingStrategy {

  ReceivableType type();

  /** Spread mensal como fração: 1,5% = 0.015. */
  BigDecimal spreadMonthly();

  /** Juros compostos mensais. {@code pow} com expoente inteiro é exato, sem arredondamento. */
  default BigDecimal discountFactor(BigDecimal baseRateMonthly, int termMonths) {
    return BigDecimal.ONE.add(baseRateMonthly).add(spreadMonthly()).pow(termMonths);
  }
}
