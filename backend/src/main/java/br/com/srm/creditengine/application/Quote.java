package br.com.srm.creditengine.application;

import br.com.srm.creditengine.domain.pricing.PricingResult;
import br.com.srm.creditengine.infrastructure.persistence.FxRate;

/**
 * @param fxRate taxa usada; {@code null} quando o pagamento é em BRL
 */
public record Quote(PricingResult pricing, FxRate fxRate) {}
