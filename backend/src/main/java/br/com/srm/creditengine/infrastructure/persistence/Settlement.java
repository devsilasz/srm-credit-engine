package br.com.srm.creditengine.infrastructure.persistence;

import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import br.com.srm.creditengine.domain.pricing.PricingResult;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Registro imutável de auditoria: sem setters e protegido por trigger no banco. */
@Entity
@Immutable
public class Settlement {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long receivableId;
  private Long assignorId;

  @Enumerated(EnumType.STRING)
  private ReceivableType receivableType;

  private BigDecimal faceValue;
  private int termMonths;
  private BigDecimal baseRateMonthly;
  private BigDecimal spreadMonthly;
  private BigDecimal presentValueBrl;
  private BigDecimal discount;

  @Enumerated(EnumType.STRING)
  private PaymentCurrency paymentCurrency;

  private BigDecimal presentValuePayment;
  private Long fxRateId;
  private BigDecimal fxRate;
  private UUID idempotencyKey;
  private String requestHash;
  private Instant settledAt;

  protected Settlement() {}

  /**
   * @param fxRate taxa usada na conversão; {@code null} em BRL
   */
  public Settlement(
      Receivable receivable,
      PricingResult pricing,
      FxRate fxRate,
      UUID idempotencyKey,
      String requestHash,
      Instant settledAt) {
    this.receivableId = receivable.getId();
    this.assignorId = receivable.getAssignorId();
    this.receivableType = receivable.getType();
    this.faceValue = pricing.faceValue();
    this.termMonths = pricing.termMonths();
    this.baseRateMonthly = pricing.baseRateMonthly();
    this.spreadMonthly = pricing.spreadMonthly();
    this.presentValueBrl = pricing.presentValueBrl();
    this.discount = pricing.discountBrl();
    this.paymentCurrency = pricing.paymentCurrency();
    this.presentValuePayment = pricing.presentValue();
    this.fxRateId = fxRate == null ? null : fxRate.getId();
    this.fxRate = fxRate == null ? null : fxRate.getRate();
    this.idempotencyKey = idempotencyKey;
    this.requestHash = requestHash;
    this.settledAt = settledAt;
  }

  public Long getId() {
    return id;
  }

  public Long getReceivableId() {
    return receivableId;
  }

  public Long getAssignorId() {
    return assignorId;
  }

  public ReceivableType getReceivableType() {
    return receivableType;
  }

  public BigDecimal getFaceValue() {
    return faceValue;
  }

  public int getTermMonths() {
    return termMonths;
  }

  public BigDecimal getBaseRateMonthly() {
    return baseRateMonthly;
  }

  public BigDecimal getSpreadMonthly() {
    return spreadMonthly;
  }

  public BigDecimal getPresentValueBrl() {
    return presentValueBrl;
  }

  public BigDecimal getDiscount() {
    return discount;
  }

  public PaymentCurrency getPaymentCurrency() {
    return paymentCurrency;
  }

  public BigDecimal getPresentValuePayment() {
    return presentValuePayment;
  }

  public Long getFxRateId() {
    return fxRateId;
  }

  public BigDecimal getFxRate() {
    return fxRate;
  }

  public UUID getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public Instant getSettledAt() {
    return settledAt;
  }
}
