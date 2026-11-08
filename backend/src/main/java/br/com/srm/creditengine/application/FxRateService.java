package br.com.srm.creditengine.application;

import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import br.com.srm.creditengine.domain.model.FxRateSource;
import br.com.srm.creditengine.domain.pricing.MonetaryPolicy;
import br.com.srm.creditengine.infrastructure.config.FxProperties;
import br.com.srm.creditengine.infrastructure.fx.ResilientFxRateClient;
import br.com.srm.creditengine.infrastructure.persistence.FxRate;
import br.com.srm.creditengine.infrastructure.persistence.FxRateRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Histórico de câmbio append-only. A taxa vigente sempre vem do banco, nunca do provedor. */
@Service
public class FxRateService {

  public static final String USD_BRL = "USD-BRL";

  /** Tempo de circuito aberto; é também o que o cliente deve esperar antes de tentar de novo. */
  public static final Duration RETRY_AFTER = Duration.ofSeconds(30);

  private final FxRateRepository rates;
  private final ResilientFxRateClient client;
  private final FxProperties properties;
  private final Clock clock;

  public FxRateService(
      FxRateRepository rates, ResilientFxRateClient client, FxProperties properties, Clock clock) {
    this.rates = rates;
    this.client = client;
    this.properties = properties;
    this.clock = clock;
  }

  /** Taxa para consulta: sem vigente é {@code 404}. */
  public FxRate current(String pair) {
    return findCurrent(pair)
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    ErrorCode.FX_RATE_NOT_FOUND, "Não há taxa vigente para " + pair + "."));
  }

  /** Taxa para precificar: sem vigente é {@code 503}, porque a operação pede dado que falta. */
  public FxRate requireCurrent(String pair) {
    return findCurrent(pair)
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    ErrorCode.FX_RATE_UNAVAILABLE,
                    "Não há taxa de câmbio vigente para "
                        + pair
                        + ". Tente novamente mais tarde."));
  }

  public FxRate createManual(String pair, BigDecimal rate, Instant validFrom) {
    if (rate.signum() <= 0) {
      throw new BusinessRuleException(
          ErrorCode.INVALID_FX_RATE, "A taxa de câmbio precisa ser maior que zero.");
    }
    return save(pair, rate, truncate(validFrom), FxRateSource.MANUAL);
  }

  public FxRate refresh() {
    BigDecimal rate = client.fetchRate(USD_BRL);
    return save(USD_BRL, rate, now(), FxRateSource.PROVIDER);
  }

  private Optional<FxRate> findCurrent(String pair) {
    Instant now = clock.instant();
    return rates
        .findFirstByPairAndValidFromLessThanEqualOrderByValidFromDescIdDesc(pair, now)
        .filter(rate -> !rate.getValidFrom().isBefore(now.minus(properties.maxAge())));
  }

  private FxRate save(String pair, BigDecimal rate, Instant validFrom, FxRateSource source) {
    BigDecimal scaled = rate.setScale(MonetaryPolicy.FX_SCALE, MonetaryPolicy.ROUNDING);
    return rates.save(new FxRate(pair, scaled, validFrom, source, now()));
  }

  private Instant now() {
    return truncate(clock.instant());
  }

  /** O Postgres guarda microssegundos: truncar evita diferença entre o valor salvo e o relido. */
  private static Instant truncate(Instant instant) {
    return instant.truncatedTo(ChronoUnit.MICROS);
  }
}
