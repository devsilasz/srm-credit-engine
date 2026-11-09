package br.com.srm.creditengine.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import br.com.srm.creditengine.domain.model.FxRateSource;
import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import br.com.srm.creditengine.domain.pricing.ChequePreDatadoStrategy;
import br.com.srm.creditengine.domain.pricing.DuplicataMercantilStrategy;
import br.com.srm.creditengine.domain.pricing.PricingEngine;
import br.com.srm.creditengine.infrastructure.persistence.FxRate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuoteServiceTest {

  private static final BigDecimal FACE = new BigDecimal("100000.00");
  private static final LocalDate IN_THREE_MONTHS = LocalDate.parse("2026-04-15");

  private final FxRateService fxRates = mock(FxRateService.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final QuoteService service =
      new QuoteService(
          new PricingEngine(
              List.of(new DuplicataMercantilStrategy(), new ChequePreDatadoStrategy()),
              new BigDecimal("0.01")),
          fxRates,
          Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC),
          meters);

  @Test
  void brlNeverLooksAtFxRates() {
    Quote quote =
        service.quote(
            ReceivableType.DUPLICATA_MERCANTIL, FACE, IN_THREE_MONTHS, PaymentCurrency.BRL);

    assertThat(quote.pricing().presentValue().toPlainString()).isEqualTo("92859.94");
    assertThat(quote.fxRate()).isNull();
    verifyNoInteractions(fxRates);
  }

  @Test
  void usdConvertsWithTheCurrentRate() {
    FxRate rate =
        new FxRate(
            "USD-BRL",
            new BigDecimal("5.43210000"),
            Instant.parse("2026-01-15T11:00:00Z"),
            FxRateSource.PROVIDER,
            Instant.parse("2026-01-15T11:00:00Z"));
    when(fxRates.requireCurrent("USD-BRL")).thenReturn(rate);

    Quote quote =
        service.quote(
            ReceivableType.DUPLICATA_MERCANTIL, FACE, IN_THREE_MONTHS, PaymentCurrency.USD);

    assertThat(quote.pricing().presentValue().toPlainString()).isEqualTo("17094.67");
    assertThat(quote.fxRate()).isSameAs(rate);
  }

  @Test
  void invalidDueDateRefusesBeforeReadingFxRates() {
    assertThatThrownBy(
            () ->
                service.quote(
                    ReceivableType.DUPLICATA_MERCANTIL,
                    FACE,
                    LocalDate.parse("2026-01-15"),
                    PaymentCurrency.USD))
        .isInstanceOfSatisfying(
            BusinessRuleException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.DUE_DATE_NOT_IN_FUTURE));
    verifyNoInteractions(fxRates);
  }

  @Test
  void timerMeasuresTheEngine() {
    service.quote(ReceivableType.CHEQUE_PRE_DATADO, FACE, IN_THREE_MONTHS, PaymentCurrency.BRL);

    assertThat(meters.get("credit_engine_pricing_duration").timer().count()).isEqualTo(1);
  }
}
