package br.com.srm.creditengine.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.srm.creditengine.AbstractIT;
import br.com.srm.creditengine.TestData;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;

/** O banco protege o registro mesmo de quem ignora a aplicação. */
class SettlementTableIT extends AbstractIT {

  @Test
  @DisplayName("AC-SET-10 trigger rejeita UPDATE direto")
  void triggerRejectsUpdate() {
    long id = TestData.settlement(jdbc, TestData.assignor(jdbc), Instant.now());

    assertThatThrownBy(
            () -> jdbc.update("UPDATE settlement SET present_value_brl = 0 WHERE id = ?", id))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("tabela imutável");
  }

  @Test
  @DisplayName("AC-SET-10 trigger rejeita DELETE direto")
  void triggerRejectsDelete() {
    long id = TestData.settlement(jdbc, TestData.assignor(jdbc), Instant.now());

    assertThatThrownBy(() -> jdbc.update("DELETE FROM settlement WHERE id = ?", id))
        .isInstanceOf(UncategorizedSQLException.class)
        .hasMessageContaining("tabela imutável");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM settlement WHERE id = ?", Long.class, id))
        .isEqualTo(1);
  }

  @Test
  void triggerRejectsTruncate() {
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE settlement"))
        .hasMessageContaining("tabela imutável");
  }

  @Test
  void fxRateHistoryIsAppendOnly() {
    long id =
        jdbc.queryForObject(
            """
            INSERT INTO fx_rate (pair, rate, valid_from, source, created_at)
            VALUES ('USD-BRL', 5.4321, now(), 'MANUAL', now())
            RETURNING id
            """,
            Long.class);

    assertThatThrownBy(() -> jdbc.update("UPDATE fx_rate SET rate = 1 WHERE id = ?", id))
        .hasMessageContaining("tabela imutável");
  }

  @Test
  void rejectsValuesThatDoNotAddUpToFace() {
    long assignorId = TestData.assignor(jdbc);
    long receivableId = TestData.settledReceivable(jdbc, assignorId);

    assertThatThrownBy(() -> insert(receivableId, assignorId, "92859.94", "1.00", "BRL"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_settlement_values_add_up");
  }

  @Test
  void rejectsUsdWithoutFxRate() {
    long assignorId = TestData.assignor(jdbc);
    long receivableId = TestData.settledReceivable(jdbc, assignorId);

    assertThatThrownBy(() -> insert(receivableId, assignorId, "92859.94", "7140.06", "USD"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_settlement_fx_iff_foreign");
  }

  private void insert(
      long receivableId, long assignorId, String pvBrl, String discount, String currency) {
    jdbc.update(
        """
        INSERT INTO settlement (receivable_id, assignor_id, receivable_type, face_value, term_months,
            base_rate_monthly, spread_monthly, present_value_brl, discount, payment_currency,
            present_value_payment, idempotency_key, request_hash, settled_at)
        VALUES (?, ?, 'DUPLICATA_MERCANTIL', 100000.00, 3, 0.01, 0.015, ?::numeric, ?::numeric, ?,
            92859.94, ?, 'hash', now())
        """,
        receivableId,
        assignorId,
        pvBrl,
        discount,
        currency,
        UUID.randomUUID());
  }
}
