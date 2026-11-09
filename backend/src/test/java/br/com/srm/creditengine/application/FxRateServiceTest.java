package br.com.srm.creditengine.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import br.com.srm.creditengine.domain.model.FxRateSource;
import br.com.srm.creditengine.infrastructure.config.FxProperties;
import br.com.srm.creditengine.infrastructure.fx.ResilientFxRateClient;
import br.com.srm.creditengine.infrastructure.persistence.FxRate;
import br.com.srm.creditengine.infrastructure.persistence.FxRateRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FxRateServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
  private static final Duration MAX_AGE = Duration.ofHours(1);
  private static final String PAIR = "USD-BRL";

  private final FxRateRepository rates = mock(FxRateRepository.class);
  private final ResilientFxRateClient client = mock(ResilientFxRateClient.class);
  private final FxRateService service =
      new FxRateService(
          rates,
          client,
          new FxProperties(
              MAX_AGE,
              Duration.ofSeconds(2),
              new FxProperties.Mock(BigDecimal.ONE, Duration.ZERO, false)),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @BeforeEach
  void savedRateIsReturned() {
    when(rates.save(any())).thenAnswer(call -> call.getArgument(0));
  }

  private void latestRateValidFrom(Instant validFrom) {
    when(rates.findFirstByPairAndValidFromLessThanEqualOrderByValidFromDescIdDesc(PAIR, NOW))
        .thenReturn(
            Optional.of(
                new FxRate(
                    PAIR, new BigDecimal("5.43210000"), validFrom, FxRateSource.PROVIDER, NOW)));
  }

  private static void assertRefused(Runnable call, ErrorCode code) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  @Test
  @DisplayName("Limite exato: taxa com idade igual ao max-age ainda vale")
  void rateAtExactlyMaxAgeIsCurrent() {
    latestRateValidFrom(NOW.minus(MAX_AGE));

    assertThat(service.requireCurrent(PAIR).getRate()).isEqualByComparingTo("5.4321");
  }

  @Test
  @DisplayName("Limite exato: um microssegundo acima do max-age, a taxa já venceu")
  void rateJustOverMaxAgeIsStale() {
    latestRateValidFrom(NOW.minus(MAX_AGE).minusNanos(1000));

    assertRefused(() -> service.requireCurrent(PAIR), ErrorCode.FX_RATE_UNAVAILABLE);
    assertRefused(() -> service.current(PAIR), ErrorCode.FX_RATE_NOT_FOUND);
  }

  @Test
  void noRateAtAllIsRefused() {
    when(rates.findFirstByPairAndValidFromLessThanEqualOrderByValidFromDescIdDesc(PAIR, NOW))
        .thenReturn(Optional.empty());

    assertRefused(() -> service.requireCurrent(PAIR), ErrorCode.FX_RATE_UNAVAILABLE);
    assertRefused(() -> service.current(PAIR), ErrorCode.FX_RATE_NOT_FOUND);
  }

  @Test
  void currentReturnsFreshRate() {
    latestRateValidFrom(NOW);

    assertThat(service.current(PAIR).getSource()).isEqualTo(FxRateSource.PROVIDER);
  }

  @Test
  void manualRateIsRefusedWhenNotPositive() {
    assertRefused(
        () -> service.createManual(PAIR, BigDecimal.ZERO, NOW), ErrorCode.INVALID_FX_RATE);
    assertRefused(
        () -> service.createManual(PAIR, new BigDecimal("-1"), NOW), ErrorCode.INVALID_FX_RATE);
    verify(rates, never()).save(any());
  }

  @Test
  @DisplayName("Taxa manual sai com escala 8 e vigência em microssegundos, como o banco guarda")
  void manualRateIsNormalizedLikeTheDatabase() {
    FxRate saved =
        service.createManual(
            PAIR, new BigDecimal("5.5"), Instant.parse("2026-01-15T11:00:00.123456789Z"));

    assertThat(saved.getRate().toPlainString()).isEqualTo("5.50000000");
    assertThat(saved.getValidFrom()).isEqualTo(Instant.parse("2026-01-15T11:00:00.123456Z"));
    assertThat(saved.getSource()).isEqualTo(FxRateSource.MANUAL);
    assertThat(saved.getPair()).isEqualTo(PAIR);
  }

  @Test
  void refreshStoresProviderRateValidFromNow() {
    when(client.fetchRate(PAIR)).thenReturn(new BigDecimal("5.4321"));

    FxRate saved = service.refresh();

    assertThat(saved.getRate().toPlainString()).isEqualTo("5.43210000");
    assertThat(saved.getValidFrom()).isEqualTo(NOW);
    assertThat(saved.getSource()).isEqualTo(FxRateSource.PROVIDER);
  }

  @Test
  void providerFailureStoresNothing() {
    when(client.fetchRate(anyString()))
        .thenThrow(new BusinessRuleException(ErrorCode.FX_PROVIDER_UNAVAILABLE, "fora"));

    assertRefused(service::refresh, ErrorCode.FX_PROVIDER_UNAVAILABLE);
    verify(rates, never()).save(any());
  }
}
