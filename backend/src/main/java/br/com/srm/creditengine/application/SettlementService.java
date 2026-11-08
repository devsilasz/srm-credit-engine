package br.com.srm.creditengine.application;

import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.infrastructure.persistence.Receivable;
import br.com.srm.creditengine.infrastructure.persistence.ReceivableRepository;
import br.com.srm.creditengine.infrastructure.persistence.Settlement;
import br.com.srm.creditengine.infrastructure.persistence.SettlementRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Liquidação atômica, idempotente e imutável.
 *
 * <p>A transação é programática porque quem perde uma corrida só pode reler a liquidação vencedora
 * depois do rollback da própria transação, que nesse ponto já está inutilizável.
 */
@Service
public class SettlementService {

  private static final Set<String> RACE_CONSTRAINTS =
      Set.of("uq_settlement_idempotency_key", "uq_settlement_receivable");

  private final ReceivableRepository receivables;
  private final SettlementRepository settlements;
  private final QuoteService quotes;
  private final TransactionTemplate transaction;
  private final Clock clock;

  public SettlementService(
      ReceivableRepository receivables,
      SettlementRepository settlements,
      QuoteService quotes,
      TransactionTemplate transaction,
      Clock clock) {
    this.receivables = receivables;
    this.settlements = settlements;
    this.quotes = quotes;
    this.transaction = transaction;
    this.clock = clock;
  }

  /**
   * @param replayed {@code true} quando a chave já tinha sido usada com o mesmo payload
   */
  public record Outcome(Settlement settlement, boolean replayed) {}

  public Outcome settle(UUID idempotencyKey, Long receivableId, PaymentCurrency currency) {
    String requestHash = requestHash(receivableId, currency);
    try {
      return transaction.execute(
          status -> settleInTransaction(idempotencyKey, requestHash, receivableId, currency));
    } catch (ObjectOptimisticLockingFailureException e) {
      return resolveLostRace(idempotencyKey, requestHash);
    } catch (DataIntegrityViolationException e) {
      if (isRaceConstraint(e)) {
        return resolveLostRace(idempotencyKey, requestHash);
      }
      throw e;
    }
  }

  public Settlement get(Long id) {
    return settlements
        .findById(id)
        .orElseThrow(
            () ->
                new BusinessRuleException(
                    ErrorCode.SETTLEMENT_NOT_FOUND, "Liquidação " + id + " não encontrada."));
  }

  private Outcome settleInTransaction(
      UUID idempotencyKey, String requestHash, Long receivableId, PaymentCurrency currency) {
    var existing = settlements.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      return replay(existing.get(), requestHash);
    }

    Receivable receivable =
        receivables
            .findById(receivableId)
            .orElseThrow(
                () ->
                    new BusinessRuleException(
                        ErrorCode.RECEIVABLE_NOT_FOUND,
                        "Recebível " + receivableId + " não encontrado."));
    // Pode ser a mesma chave: a leitura acima veio antes do commit da vencedora, e esta veio
    // depois.
    if (receivable.isSettled()) {
      return resolveLostRace(idempotencyKey, requestHash);
    }

    Quote quote =
        quotes.quote(
            receivable.getType(), receivable.getFaceValue(), receivable.getDueDate(), currency);

    // O UPDATE vem antes do INSERT de propósito: a concorrente espera o lock da linha e perde no
    // @Version. Com o INSERT primeiro, a UNIQUE decidiria antes e o @Version nunca dispararia.
    receivable.markSettled();
    receivables.saveAndFlush(receivable);
    Settlement settlement =
        settlements.saveAndFlush(
            new Settlement(
                receivable,
                quote.pricing(),
                quote.fxRate(),
                idempotencyKey,
                requestHash,
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
    return new Outcome(settlement, false);
  }

  /** A vencedora já commitou: ou esta é a mesma requisição (replay), ou o recebível já foi pago. */
  private Outcome resolveLostRace(UUID idempotencyKey, String requestHash) {
    return settlements
        .findByIdempotencyKey(idempotencyKey)
        .map(winner -> replay(winner, requestHash))
        .orElseThrow(SettlementService::alreadySettled);
  }

  private static Outcome replay(Settlement existing, String requestHash) {
    if (!existing.getRequestHash().equals(requestHash)) {
      throw new BusinessRuleException(
          ErrorCode.IDEMPOTENCY_KEY_REUSED,
          "Esta Idempotency-Key já foi usada com outro recebível ou outra moeda.");
    }
    return new Outcome(existing, true);
  }

  private static BusinessRuleException alreadySettled() {
    return new BusinessRuleException(
        ErrorCode.RECEIVABLE_ALREADY_SETTLED, "Este recebível já foi liquidado.");
  }

  /** Só as duas UNIQUE indicam corrida. Qualquer outra constraint é bug e precisa virar 500. */
  private static boolean isRaceConstraint(DataIntegrityViolationException e) {
    return e.getCause() instanceof ConstraintViolationException violation
        && violation.getConstraintName() != null
        && RACE_CONSTRAINTS.contains(violation.getConstraintName());
  }

  /** Payload canônico com campos em ordem fixa, para o hash não depender da forma do JSON. */
  private static String requestHash(Long receivableId, PaymentCurrency currency) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((receivableId + "|" + currency).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", e);
    }
  }
}
