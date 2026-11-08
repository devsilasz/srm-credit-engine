package br.com.srm.creditengine.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TermCalculatorTest {

  @ParameterizedTest(name = "{0} -> {1} = {2} meses")
  @CsvSource({
    "2026-01-15, 2026-04-15, 3", // AC-PRC-08 meses cheios
    "2026-01-15, 2026-03-16, 3", // AC-PRC-09 dois meses e um dia arredonda para cima
    "2026-01-15, 2026-01-16, 1", // um dia ainda é um mês
    "2026-01-31, 2026-02-28, 1", // fim de mês: hoje + 1 mês cai em 28/02
    "2026-01-31, 2026-04-30, 3", // fim de mês: hoje + 3 meses cai em 30/04
    "2026-01-30, 2026-03-01, 2", // 28/02 + 1 dia passa de um mês cheio
    "2026-01-15, 2056-01-15, 360", // limite aceito
  })
  @DisplayName("AC-PRC-08 e AC-PRC-09 prazo em meses, arredondado para cima")
  void roundsTermUp(LocalDate today, LocalDate dueDate, int expected) {
    assertThat(TermCalculator.termMonths(today, dueDate)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"2026-01-15, 2026-01-15", "2026-01-15, 2026-01-14"})
  @DisplayName("AC-PRC-10 vencimento em hoje ou no passado é recusado")
  void refusesDueDateNotInFuture(LocalDate today, LocalDate dueDate) {
    assertThatThrownBy(() -> TermCalculator.termMonths(today, dueDate))
        .isInstanceOfSatisfying(
            BusinessRuleException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.DUE_DATE_NOT_IN_FUTURE));
  }

  @Test
  @DisplayName("AC-PRC-16 prazo acima de 360 meses é recusado")
  void refusesTermAboveLimit() {
    LocalDate today = LocalDate.parse("2026-01-15");

    assertThatThrownBy(() -> TermCalculator.termMonths(today, today.plusMonths(360).plusDays(1)))
        .isInstanceOfSatisfying(
            BusinessRuleException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.DUE_DATE_TOO_FAR));
  }
}
