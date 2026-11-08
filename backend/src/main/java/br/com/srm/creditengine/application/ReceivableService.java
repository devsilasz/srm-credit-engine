package br.com.srm.creditengine.application;

import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import br.com.srm.creditengine.domain.model.ReceivableType;
import br.com.srm.creditengine.domain.pricing.MonetaryPolicy;
import br.com.srm.creditengine.domain.pricing.TermCalculator;
import br.com.srm.creditengine.infrastructure.persistence.AssignorRepository;
import br.com.srm.creditengine.infrastructure.persistence.Receivable;
import br.com.srm.creditengine.infrastructure.persistence.ReceivableRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class ReceivableService {

  private final ReceivableRepository receivables;
  private final AssignorRepository assignors;
  private final Clock clock;

  public ReceivableService(
      ReceivableRepository receivables, AssignorRepository assignors, Clock clock) {
    this.receivables = receivables;
    this.assignors = assignors;
    this.clock = clock;
  }

  public Receivable create(
      Long assignorId, ReceivableType type, BigDecimal faceValue, LocalDate dueDate) {
    if (!assignors.existsById(assignorId)) {
      throw new BusinessRuleException(
          ErrorCode.ASSIGNOR_NOT_FOUND, "Cedente " + assignorId + " não encontrado.");
    }
    TermCalculator.termMonths(LocalDate.now(clock), dueDate);
    return receivables.save(
        new Receivable(
            assignorId,
            type,
            faceValue.setScale(MonetaryPolicy.SCALE, RoundingMode.UNNECESSARY),
            dueDate,
            clock.instant().truncatedTo(ChronoUnit.MICROS)));
  }

  public Receivable get(Long id) {
    return receivables
        .findById(id)
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    ErrorCode.RECEIVABLE_NOT_FOUND, "Recebível " + id + " não encontrado."));
  }
}
