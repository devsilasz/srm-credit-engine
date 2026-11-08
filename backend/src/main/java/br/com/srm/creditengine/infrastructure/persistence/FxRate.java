package br.com.srm.creditengine.infrastructure.persistence;

import br.com.srm.creditengine.domain.model.FxRateSource;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** Linha do histórico de câmbio. Uma taxa nova é sempre uma linha nova. */
@Entity
@Immutable
public class FxRate {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String pair;
  private BigDecimal rate;
  private Instant validFrom;

  @Enumerated(EnumType.STRING)
  private FxRateSource source;

  private Instant createdAt;

  protected FxRate() {}

  public FxRate(
      String pair, BigDecimal rate, Instant validFrom, FxRateSource source, Instant createdAt) {
    this.pair = pair;
    this.rate = rate;
    this.validFrom = validFrom;
    this.source = source;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getPair() {
    return pair;
  }

  public BigDecimal getRate() {
    return rate;
  }

  public Instant getValidFrom() {
    return validFrom;
  }

  public FxRateSource getSource() {
    return source;
  }
}
