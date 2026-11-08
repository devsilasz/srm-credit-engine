package br.com.srm.creditengine.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingEngineTest {

  private final PricingEngine engine =
      new PricingEngine(
          List.of(new DuplicataMercantilStrategy(), new ChequePreDatadoStrategy()),
          new BigDecimal("0.01"));

  @ParameterizedTest(name = "{0} / {1} = {2}")
  @CsvSource({"2.01, 2, 1.00", "2.03, 2, 1.02"})
  @DisplayName("AC-PRC-04 empate exato arredonda para o dígito par")
  void halfEvenOnExactTie(BigDecimal amount, BigDecimal divisor, String expected) {
    assertThat(MonetaryPolicy.divideToMoney(amount, divisor).toPlainString()).isEqualTo(expected);
  }

  @Test
  @DisplayName("AC-PRC-05 a conversão usa o PV em BRL já arredondado, não o valor cheio")
  void convertsRoundedPresentValue() {
    // PV cheio = 92859.9419...; a taxa 0.1 amplifica os centavos: 928599.42 se usasse o valor
    // cheio.
    PricingResult result =
        engine.price(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("100000.00"),
            3,
            PaymentCurrency.USD,
            new BigDecimal("0.1"));

    assertThat(result.presentValueBrl().toPlainString()).isEqualTo("92859.94");
    assertThat(result.presentValue().toPlainString()).isEqualTo("928599.40");
  }

  @Test
  @DisplayName("AC-PRC-06 face mínimo com dízima não lança ArithmeticException")
  void minimumFaceValueWithRepeatingDecimal() {
    PricingResult result =
        engine.price(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("0.01"),
            1,
            PaymentCurrency.BRL,
            null);

    assertThat(result.presentValue().toPlainString()).isEqualTo("0.01");
    assertThat(result.discountBrl().toPlainString()).isEqualTo("0.00");
  }

  @Test
  @DisplayName("AC-PRC-07 prazo de 360 meses com face alto mantém escala 2")
  void largeFactorKeepsPrecision() {
    PricingResult result =
        engine.price(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("1000000000.00"),
            360,
            PaymentCurrency.BRL,
            null);

    // 1e9 / 1.025^360 = 137850.5359..., calculado com Python decimal (prec=2000), fora da JVM.
    assertThat(result.presentValue().toPlainString()).isEqualTo("137850.54");
    assertThat(result.presentValue().scale()).isEqualTo(2);
  }

  @Test
  void acceptsFxRateWithEightDecimals() {
    PricingResult result =
        engine.price(
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("100000.00"),
            3,
            PaymentCurrency.USD,
            new BigDecimal("5.43210001"));

    assertThat(result.presentValue().toPlainString()).isEqualTo("17094.67");
  }

  @Test
  @DisplayName("AC-PRC-17 PV que arredonda para zero na moeda de pagamento é recusado")
  void refusesPresentValueRoundedToZero() {
    assertThatThrownBy(
            () ->
                engine.price(
                    ReceivableType.DUPLICATA_MERCANTIL,
                    new BigDecimal("0.01"),
                    1,
                    PaymentCurrency.USD,
                    new BigDecimal("5.4321")))
        .isInstanceOfSatisfying(
            BusinessRuleException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.PRESENT_VALUE_TOO_SMALL));
  }

  @Test
  void spreadsAreFractionsNotPercentages() {
    assertThat(new DuplicataMercantilStrategy().spreadMonthly()).isEqualByComparingTo("0.015");
    assertThat(new ChequePreDatadoStrategy().spreadMonthly()).isEqualByComparingTo("0.025");
  }

  @Test
  void resultCarriesRatesUsedWithFixedScale() {
    PricingResult result =
        engine.price(
            ReceivableType.CHEQUE_PRE_DATADO,
            new BigDecimal("25000.00"),
            2,
            PaymentCurrency.BRL,
            null);

    assertThat(result.baseRateMonthly().toPlainString()).isEqualTo("0.010000");
    assertThat(result.spreadMonthly().toPlainString()).isEqualTo("0.025000");
    assertThat(result.termMonths()).isEqualTo(2);
  }

  @Test
  void failsAtStartupWhenATypeHasNoStrategy() {
    List<PricingStrategy> onlyDuplicata = List.of(new DuplicataMercantilStrategy());

    assertThatThrownBy(() -> new PricingEngine(onlyDuplicata, new BigDecimal("0.01")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CHEQUE_PRE_DATADO");
  }
}
