package br.com.srm.creditengine.domain.pricing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Único lugar com regra de arredondamento. Só o resultado final de cada moeda é arredondado. */
public final class MonetaryPolicy {

  public static final int SCALE = 2;
  public static final int RATE_SCALE = 6;
  public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
  public static final MathContext CALC = MathContext.DECIMAL128;

  private MonetaryPolicy() {}

  public static BigDecimal divideToMoney(BigDecimal amount, BigDecimal divisor) {
    return amount.divide(divisor, CALC).setScale(SCALE, ROUNDING);
  }
}
