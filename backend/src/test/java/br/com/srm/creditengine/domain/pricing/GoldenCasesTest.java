package br.com.srm.creditengine.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Aferição do enunciado (seção 4.3). Se falhar, o bug está no motor: nunca ajuste estes valores.
 */
class GoldenCasesTest {

  private final PricingEngine engine =
      new PricingEngine(
          List.of(new DuplicataMercantilStrategy(), new ChequePreDatadoStrategy()),
          new BigDecimal("0.01"));

  @ParameterizedTest(name = "{0}: PV={6}")
  @CsvSource({
    "C1, DUPLICATA_MERCANTIL, 100000.00, 3, BRL,       , 92859.94, 7140.06",
    "C2, CHEQUE_PRE_DATADO,    25000.00, 2, BRL,       , 23337.77, 1662.23",
    "C3, DUPLICATA_MERCANTIL, 100000.00, 3, USD, 5.4321, 17094.67, 7140.06",
  })
  void reproducesGoldenCaseToTheCent(
      String id,
      ReceivableType type,
      BigDecimal faceValue,
      int termMonths,
      PaymentCurrency currency,
      BigDecimal fxRate,
      String expectedPresentValue,
      String expectedDiscount) {
    PricingResult result = engine.price(type, faceValue, termMonths, currency, fxRate);

    assertThat(result.presentValue().toPlainString()).isEqualTo(expectedPresentValue);
    assertThat(result.discountBrl().toPlainString()).isEqualTo(expectedDiscount);
  }
}
