package br.com.srm.creditengine.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.srm.creditengine.domain.model.FxRateSource;
import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import br.com.srm.creditengine.domain.pricing.PricingResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** O registro de auditoria precisa guardar exatamente o que foi usado no cálculo. */
class SettlementEntityTest {

  private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
  private static final UUID KEY = UUID.randomUUID();

  private static Receivable receivable() {
    Receivable receivable =
        new Receivable(
            3L,
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("100000.00"),
            LocalDate.parse("2026-04-15"),
            NOW);
    ReflectionTestUtils.setField(receivable, "id", 7L);
    return receivable;
  }

  private static PricingResult pricing(PaymentCurrency currency, String presentValue) {
    return new PricingResult(
        new BigDecimal("100000.00"),
        3,
        new BigDecimal("0.010000"),
        new BigDecimal("0.015000"),
        new BigDecimal("92859.94"),
        new BigDecimal("7140.06"),
        currency,
        new BigDecimal(presentValue));
  }

  @Test
  @DisplayName("AC-SET-02 em USD, guarda o id e o valor da taxa usada")
  void usdKeepsTheRateUsed() {
    FxRate rate =
        new FxRate("USD-BRL", new BigDecimal("5.43210000"), NOW, FxRateSource.PROVIDER, NOW);
    ReflectionTestUtils.setField(rate, "id", 11L);

    Settlement settlement =
        new Settlement(
            receivable(), pricing(PaymentCurrency.USD, "17094.67"), rate, KEY, "hash", NOW);

    assertThat(settlement.getFxRateId()).isEqualTo(11L);
    assertThat(settlement.getFxRate()).isEqualByComparingTo("5.4321");
    assertThat(settlement.getPresentValuePayment()).isEqualByComparingTo("17094.67");
    assertThat(settlement.getPresentValueBrl()).isEqualByComparingTo("92859.94");
    assertThat(settlement.getPaymentCurrency()).isEqualTo(PaymentCurrency.USD);
  }

  @Test
  @DisplayName("AC-SET-02 em BRL, não há taxa, e o resto é copiado do cálculo e do recebível")
  void brlHasNoRateAndCopiesTheCalculation() {
    Settlement settlement =
        new Settlement(
            receivable(), pricing(PaymentCurrency.BRL, "92859.94"), null, KEY, "hash", NOW);

    assertThat(settlement.getFxRateId()).isNull();
    assertThat(settlement.getFxRate()).isNull();
    assertThat(settlement.getReceivableId()).isEqualTo(7L);
    assertThat(settlement.getAssignorId()).isEqualTo(3L);
    assertThat(settlement.getReceivableType()).isEqualTo(ReceivableType.DUPLICATA_MERCANTIL);
    assertThat(settlement.getTermMonths()).isEqualTo(3);
    assertThat(settlement.getBaseRateMonthly()).isEqualByComparingTo("0.01");
    assertThat(settlement.getSpreadMonthly()).isEqualByComparingTo("0.015");
    assertThat(settlement.getDiscount()).isEqualByComparingTo("7140.06");
    assertThat(settlement.getFaceValue()).isEqualByComparingTo("100000.00");
    assertThat(settlement.getIdempotencyKey()).isEqualTo(KEY);
    assertThat(settlement.getRequestHash()).isEqualTo("hash");
    assertThat(settlement.getSettledAt()).isEqualTo(NOW);
  }

  @Test
  void receivableStartsOpenAndSettlesOnce() {
    Receivable receivable = receivable();

    assertThat(receivable.isSettled()).isFalse();
    receivable.markSettled();
    assertThat(receivable.isSettled()).isTrue();
    assertThat(receivable.getCreatedAt()).isEqualTo(NOW);
  }
}
