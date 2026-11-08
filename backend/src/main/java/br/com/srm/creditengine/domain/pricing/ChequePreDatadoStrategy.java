package br.com.srm.creditengine.domain.pricing;

import br.com.srm.creditengine.domain.model.ReceivableType;
import java.math.BigDecimal;

public final class ChequePreDatadoStrategy implements PricingStrategy {

  private static final BigDecimal SPREAD_MONTHLY = new BigDecimal("0.025");

  @Override
  public ReceivableType type() {
    return ReceivableType.CHEQUE_PRE_DATADO;
  }

  @Override
  public BigDecimal spreadMonthly() {
    return SPREAD_MONTHLY;
  }
}
