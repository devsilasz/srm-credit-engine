package br.com.srm.creditengine.domain.pricing;

import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Prazo em meses inteiros, arredondado para cima: conservador para o fundo. */
public final class TermCalculator {

  /** Maior prazo aceito. Também limita o custo de {@code pow} para vencimentos absurdos. */
  public static final int MAX_TERM_MONTHS = 360;

  private TermCalculator() {}

  public static int termMonths(LocalDate today, LocalDate dueDate) {
    if (!dueDate.isAfter(today)) {
      throw new BusinessRuleException(
          ErrorCode.DUE_DATE_NOT_IN_FUTURE, "O vencimento precisa ser posterior a hoje.");
    }
    long months = ChronoUnit.MONTHS.between(today, dueDate);
    if (today.plusMonths(months).isBefore(dueDate)) {
      months++;
    }
    if (months > MAX_TERM_MONTHS) {
      throw new BusinessRuleException(
          ErrorCode.DUE_DATE_TOO_FAR, "O prazo máximo é de " + MAX_TERM_MONTHS + " meses.");
    }
    return (int) months;
  }
}
