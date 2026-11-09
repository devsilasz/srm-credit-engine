package br.com.srm.creditengine.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import br.com.srm.creditengine.domain.pricing.PricingResult;
import br.com.srm.creditengine.infrastructure.persistence.Receivable;
import br.com.srm.creditengine.infrastructure.persistence.ReceivableRepository;
import br.com.srm.creditengine.infrastructure.persistence.Settlement;
import br.com.srm.creditengine.infrastructure.persistence.SettlementRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * As decisões de corrida, de forma determinística. O teste de concorrência real depende do
 * escalonamento das threads: aqui, cada intercalação é montada de propósito.
 */
class SettlementServiceTest {

  private static final Long RECEIVABLE_ID = 7L;
  private static final UUID KEY = UUID.randomUUID();
  private static final String DUPLICATA = "DUPLICATA_MERCANTIL";

  private final ReceivableRepository receivables = mock(ReceivableRepository.class);
  private final SettlementRepository settlements = mock(SettlementRepository.class);
  private final QuoteService quotes = mock(QuoteService.class);
  private final TransactionTemplate transaction = mock(TransactionTemplate.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final SettlementService service =
      new SettlementService(
          receivables,
          settlements,
          quotes,
          transaction,
          Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC),
          meters);

  private Receivable receivable;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    receivable =
        new Receivable(
            1L,
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("100000.00"),
            LocalDate.parse("2026-04-15"),
            Instant.parse("2026-01-15T12:00:00Z"));
    ReflectionTestUtils.setField(receivable, "id", RECEIVABLE_ID);
    when(transaction.execute(any()))
        .thenAnswer(call -> call.getArgument(0, TransactionCallback.class).doInTransaction(null));
    when(settlements.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
    when(receivables.findById(RECEIVABLE_ID)).thenReturn(Optional.of(receivable));
    when(quotes.quote(any(), any(), any(), any()))
        .thenAnswer(
            call ->
                new Quote(
                    new PricingResult(
                        new BigDecimal("100000.00"),
                        3,
                        new BigDecimal("0.010000"),
                        new BigDecimal("0.015000"),
                        new BigDecimal("92859.94"),
                        new BigDecimal("7140.06"),
                        call.getArgument(3),
                        new BigDecimal("92859.94")),
                    null));
    when(settlements.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              Settlement saved = call.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", 42L);
              return saved;
            });
  }

  /** Uma liquidação verdadeira, com o hash que o serviço calcula para este payload. */
  private Settlement existingSettlement(PaymentCurrency currency) {
    Settlement existing = service.settle(UUID.randomUUID(), RECEIVABLE_ID, currency).settlement();
    ReflectionTestUtils.setField(receivable, "status", null);
    return existing;
  }

  private double counted(String outcome, String type) {
    var counter =
        meters
            .find("credit_engine_settlements")
            .tag("outcome", outcome)
            .tag("type", type)
            .counter();
    return counter == null ? 0 : counter.count();
  }

  private static void assertRefused(Runnable call, ErrorCode code) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo(code));
  }

  private static DataIntegrityViolationException violationOf(String constraint) {
    return new DataIntegrityViolationException(
        "violação", new ConstraintViolationException("v", new SQLException(), constraint));
  }

  @Test
  @DisplayName("R3 o UPDATE do recebível vem antes do INSERT: é o que faz o @Version decidir")
  void updatesReceivableBeforeInsertingSettlement() {
    SettlementService.Outcome outcome = service.settle(KEY, RECEIVABLE_ID, PaymentCurrency.BRL);

    assertThat(outcome.replayed()).isFalse();
    assertThat(receivable.isSettled()).isTrue();
    InOrder order = inOrder(receivables, settlements);
    order.verify(receivables).saveAndFlush(receivable);
    order.verify(settlements).saveAndFlush(any());
    assertThat(counted("success", DUPLICATA)).isEqualTo(1);
  }

  @Test
  @DisplayName("Mesma chave e mesmo payload devolvem a original, sem gravar")
  void sameKeySamePayloadReplays() {
    Settlement existing = existingSettlement(PaymentCurrency.BRL);
    when(settlements.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));

    SettlementService.Outcome outcome = service.settle(KEY, RECEIVABLE_ID, PaymentCurrency.BRL);

    assertThat(outcome.replayed()).isTrue();
    assertThat(outcome.settlement()).isSameAs(existing);
    assertThat(counted("replay", DUPLICATA)).isEqualTo(1);
  }

  @Test
  @DisplayName("Mesma chave com outra moeda é recusada, mesmo que o recebível exista")
  void sameKeyOtherPayloadIsRefused() {
    Settlement existing = existingSettlement(PaymentCurrency.BRL);
    when(settlements.findByIdempotencyKey(KEY)).thenReturn(Optional.of(existing));

    assertRefused(
        () -> service.settle(KEY, RECEIVABLE_ID, PaymentCurrency.USD),
        ErrorCode.IDEMPOTENCY_KEY_REUSED);
    assertThat(counted("idempotency_key_reused", "UNKNOWN")).isEqualTo(1);
  }

  @Test
  void unknownReceivableIsRefused() {
    when(receivables.findById(RECEIVABLE_ID)).thenReturn(Optional.empty());

    assertRefused(
        () -> service.settle(KEY, RECEIVABLE_ID, PaymentCurrency.BRL),
        ErrorCode.RECEIVABLE_NOT_FOUND);
    verify(settlements, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Recebível já pago com outra chave é 409")
  void settledReceivableWithOtherKeyIsConflict() {
    receivable.markSettled();

    assertRefused(
        () -> service.settle(KEY, RECEIVABLE_ID, PaymentCurrency.BRL),
        ErrorCode.RECEIVABLE_ALREADY_SETTLED);
    assertThat(counted("receivable_already_settled", DUPLICATA)).isEqualTo(1);
  }

  @Test
  @DisplayName("Regressão: a chave some na 1ª leitura, o recebível já está pago na 2ª: é replay")
  void keyAppearsBetweenTheTwoReads() {
    Settlement winner = existingSettlement(PaymentCurrency.BRL);
    receivable.markSettled();
    when(settlements.findByIdempotencyKey(KEY))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(winner));

    SettlementService.Outcome outcome = service.settle(KEY, RECEIVABLE_ID, PaymentCurrency.BRL);

    assertThat(outcome.replayed()).isTrue();
    assertThat(outcome.settlement()).isSameAs(winner);
  }

  @Test
  @DisplayName("Perdeu no @Version para outra chave: 409")
  void lostOnVersionToOtherKeyIsConflict() {
    when(receivables.saveAndFlush(receivable))
        .thenThrow(new ObjectOptimisticLockingFailureException(Receivable.class, RECEIVABLE_ID));

    assertRefused(
        () -> service.settle(KEY, RECEIVABLE_ID, PaymentCurrency.BRL),
        ErrorCode.RECEIVABLE_ALREADY_SETTLED);
    verify(settlements, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("Perdeu no @Version para a mesma chave: replay da vencedora")
  void lostOnVersionToSameKeyReplays() {
    Settlement winner = existingSettlement(PaymentCurrency.BRL);
    when(receivables.saveAndFlush(receivable))
        .thenThrow(new ObjectOptimisticLockingFailureException(Receivable.class, RECEIVABLE_ID));
    when(settlements.findByIdempotencyKey(KEY))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(winner));

    assertThat(service.settle(KEY, RECEIVABLE_ID, PaymentCurrency.BRL).settlement())
        .isSameAs(winner);
  }

  @Test
  @DisplayName("Violou a UNIQUE da chave: a resolução relê e devolve a vencedora")
  void uniqueKeyViolationReplays() {
    Settlement winner = existingSettlement(PaymentCurrency.BRL);
    doThrow(violationOf("uq_settlement_idempotency_key")).when(settlements).saveAndFlush(any());
    when(settlements.findByIdempotencyKey(KEY))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(winner));

    assertThat(service.settle(KEY, RECEIVABLE_ID, PaymentCurrency.BRL).replayed()).isTrue();
  }

  @Test
  @DisplayName("Violou a UNIQUE do recebível: rede de segurança, 409")
  void uniqueReceivableViolationIsConflict() {
    doThrow(violationOf("uq_settlement_receivable")).when(settlements).saveAndFlush(any());

    assertRefused(
        () -> service.settle(KEY, RECEIVABLE_ID, PaymentCurrency.BRL),
        ErrorCode.RECEIVABLE_ALREADY_SETTLED);
  }

  @Test
  @DisplayName("Outra constraint é bug: sobe como erro, e nunca vira 409")
  void otherConstraintIsNotMaskedAsConflict() {
    DataIntegrityViolationException violation = violationOf("ck_settlement_values_add_up");
    doThrow(violation).when(settlements).saveAndFlush(any());

    assertThatThrownBy(() -> service.settle(KEY, RECEIVABLE_ID, PaymentCurrency.BRL))
        .isSameAs(violation);
    assertThat(counted("error", DUPLICATA)).isEqualTo(1);
  }

  @Test
  void constraintWithoutNameIsNotAConflict() {
    DataIntegrityViolationException violation = violationOf(null);
    doThrow(violation).when(settlements).saveAndFlush(any());

    assertThatThrownBy(() -> service.settle(KEY, RECEIVABLE_ID, PaymentCurrency.BRL))
        .isSameAs(violation);
  }

  @Test
  void integrityErrorWithoutHibernateCauseIsNotAConflict() {
    DataIntegrityViolationException violation = new DataIntegrityViolationException("outra");
    doThrow(violation).when(settlements).saveAndFlush(any());

    assertThatThrownBy(() -> service.settle(KEY, RECEIVABLE_ID, PaymentCurrency.BRL))
        .isSameAs(violation);
  }

  @Test
  void getReturnsSettlementOrRefuses() {
    Settlement existing = existingSettlement(PaymentCurrency.BRL);
    when(settlements.findById(42L)).thenReturn(Optional.of(existing));

    assertThat(service.get(42L)).isSameAs(existing);
    assertRefused(() -> service.get(1L), ErrorCode.SETTLEMENT_NOT_FOUND);
  }
}
