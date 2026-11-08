package br.com.srm.creditengine.domain.pricing;

import br.com.srm.creditengine.domain.model.ReceivableType;
import java.math.BigDecimal;

public final class DuplicataMercantilStrategy implements PricingStrategy {

  private static final BigDecimal SPREAD_MONTHLY = new BigDecimal("0.015");

  @Override
  public ReceivableType type() {
    return ReceivableType.DUPLICATA_MERCANTIL;
  }

  @Override
  public BigDecimal spreadMonthly() {
    return SPREAD_MONTHLY;
  }
}
