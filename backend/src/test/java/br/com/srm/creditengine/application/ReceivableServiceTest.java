package br.com.srm.creditengine.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import br.com.srm.creditengine.domain.model.ReceivableStatus;
import br.com.srm.creditengine.domain.model.ReceivableType;
import br.com.srm.creditengine.infrastructure.persistence.AssignorRepository;
import br.com.srm.creditengine.infrastructure.persistence.Receivable;
import br.com.srm.creditengine.infrastructure.persistence.ReceivableRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReceivableServiceTest {

  private static final LocalDate TODAY = LocalDate.parse("2026-01-15");

  private final ReceivableRepository receivables = mock(ReceivableRepository.class);
  private final AssignorRepository assignors = mock(AssignorRepository.class);
  private final ReceivableService service =
      new ReceivableService(
          receivables,
          assignors,
          Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC));

  private static void assertRefused(Runnable call, ErrorCode code) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  @Test
  void createsOpenReceivableWithScaleTwo() {
    when(assignors.existsById(1L)).thenReturn(true);
    when(receivables.save(any())).thenAnswer(call -> call.getArgument(0));

    Receivable created =
        service.create(
            1L, ReceivableType.CHEQUE_PRE_DATADO, new BigDecimal("25000"), TODAY.plusMonths(2));

    assertThat(created.getFaceValue().toPlainString()).isEqualTo("25000.00");
    assertThat(created.getStatus()).isEqualTo(ReceivableStatus.OPEN);
    assertThat(created.getAssignorId()).isEqualTo(1L);
    assertThat(created.getType()).isEqualTo(ReceivableType.CHEQUE_PRE_DATADO);
    assertThat(created.getDueDate()).isEqualTo(TODAY.plusMonths(2));
    assertThat(created.isSettled()).isFalse();
  }

  @Test
  void unknownAssignorIsRefusedAndNothingIsSaved() {
    when(assignors.existsById(9L)).thenReturn(false);

    assertRefused(
        () ->
            service.create(9L, ReceivableType.CHEQUE_PRE_DATADO, BigDecimal.TEN, TODAY.plusDays(1)),
        ErrorCode.ASSIGNOR_NOT_FOUND);
    verify(receivables, never()).save(any());
  }

  @Test
  void dueDateNotInFutureIsRefusedAndNothingIsSaved() {
    when(assignors.existsById(1L)).thenReturn(true);

    assertRefused(
        () -> service.create(1L, ReceivableType.CHEQUE_PRE_DATADO, BigDecimal.TEN, TODAY),
        ErrorCode.DUE_DATE_NOT_IN_FUTURE);
    verify(receivables, never()).save(any());
  }

  @Test
  void getRefusesUnknownReceivable() {
    when(receivables.findById(1L)).thenReturn(Optional.empty());

    assertRefused(() -> service.get(1L), ErrorCode.RECEIVABLE_NOT_FOUND);
  }
}
