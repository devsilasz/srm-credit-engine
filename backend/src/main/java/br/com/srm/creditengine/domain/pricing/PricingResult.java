package br.com.srm.creditengine.domain.pricing;

import br.com.srm.creditengine.domain.model.PaymentCurrency;
import java.math.BigDecimal;

/**
 * @param presentValueBrl PV em BRL, base do deságio
 * @param presentValue PV na moeda de pagamento (igual a {@code presentValueBrl} em BRL)
 * @param discountBrl deságio, sempre em BRL
 */
public record PricingResult(
    BigDecimal faceValue,
    int termMonths,
    BigDecimal baseRateMonthly,
    BigDecimal spreadMonthly,
    BigDecimal presentValueBrl,
    BigDecimal discountBrl,
    PaymentCurrency paymentCurrency,
    BigDecimal presentValue) {}
