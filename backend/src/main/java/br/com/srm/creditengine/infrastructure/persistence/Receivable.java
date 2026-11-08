package br.com.srm.creditengine.infrastructure.persistence;

import br.com.srm.creditengine.domain.model.ReceivableStatus;
import br.com.srm.creditengine.domain.model.ReceivableType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
public class Receivable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long assignorId;

  @Enumerated(EnumType.STRING)
  private ReceivableType type;

  private BigDecimal faceValue;
  private LocalDate dueDate;

  @Enumerated(EnumType.STRING)
  private ReceivableStatus status;

  /** Defesa principal contra duas liquidações concorrentes do mesmo recebível. */
  @Version private long version;

  private Instant createdAt;

  protected Receivable() {}

  public Receivable(
      Long assignorId,
      ReceivableType type,
      BigDecimal faceValue,
      LocalDate dueDate,
      Instant createdAt) {
    this.assignorId = assignorId;
    this.type = type;
    this.faceValue = faceValue;
    this.dueDate = dueDate;
    this.status = ReceivableStatus.OPEN;
    this.createdAt = createdAt;
  }

  public void markSettled() {
    this.status = ReceivableStatus.SETTLED;
  }

  public boolean isSettled() {
    return status == ReceivableStatus.SETTLED;
  }

  public Long getId() {
    return id;
  }

  public Long getAssignorId() {
    return assignorId;
  }

  public ReceivableType getType() {
    return type;
  }

  public BigDecimal getFaceValue() {
    return faceValue;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public ReceivableStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
