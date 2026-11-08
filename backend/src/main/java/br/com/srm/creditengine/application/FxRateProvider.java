package br.com.srm.creditengine.application;

import java.math.BigDecimal;

/** Fonte externa de câmbio. Hoje é um mock, que no mundo real seria um provedor de mercado. */
public interface FxRateProvider {

  /**
   * @return BRL por 1 unidade da moeda cotada no par
   */
  BigDecimal fetchRate(String pair);
}
