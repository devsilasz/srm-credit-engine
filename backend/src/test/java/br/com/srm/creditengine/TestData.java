package br.com.srm.creditengine;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dados de teste via SQL. Cada teste cria o próprio cedente em vez de limpar o banco, porque as
 * tabelas de auditoria são imutáveis mesmo aqui.
 */
public final class TestData {

  private TestData() {}

  public static long assignor(JdbcTemplate jdbc) {
    String unique = UUID.randomUUID().toString();
    return jdbc.queryForObject(
        "INSERT INTO assignor (name, document) VALUES (?, ?) RETURNING id",
        Long.class,
        "Cedente " + unique.substring(0, 8),
        unique.replace("-", "").substring(0, 14));
  }

  /** Taxa conhecida e vigente agora, para que o teste não dependa da ordem de execução. */
  public static long fxRate(JdbcTemplate jdbc, String rate, Instant validFrom) {
    return jdbc.queryForObject(
        """
        INSERT INTO fx_rate (pair, rate, valid_from, source, created_at)
        VALUES ('USD-BRL', ?::numeric, ?, 'MANUAL', now())
        RETURNING id
        """,
        Long.class,
        rate,
        OffsetDateTime.ofInstant(validFrom, ZoneOffset.UTC));
  }

  public static long settledReceivable(JdbcTemplate jdbc, long assignorId) {
    return jdbc.queryForObject(
        """
        INSERT INTO receivable (assignor_id, type, face_value, due_date, status, version, created_at)
        VALUES (?, 'DUPLICATA_MERCANTIL', 100000.00, DATE '2030-01-01', 'SETTLED', 1, now())
        RETURNING id
        """,
        Long.class,
        assignorId);
  }

  /** Liquidação em BRL coerente com C1, inserida direto para montar cenários de extrato. */
  public static long settlement(JdbcTemplate jdbc, long assignorId, Instant settledAt) {
    long receivableId = settledReceivable(jdbc, assignorId);
    return jdbc.queryForObject(
        """
        INSERT INTO settlement (receivable_id, assignor_id, receivable_type, face_value, term_months,
            base_rate_monthly, spread_monthly, present_value_brl, discount, payment_currency,
            present_value_payment, idempotency_key, request_hash, settled_at)
        VALUES (?, ?, 'DUPLICATA_MERCANTIL', 100000.00, 3, 0.01, 0.015, 92859.94, 7140.06, 'BRL',
            92859.94, ?, 'hash', ?)
        RETURNING id
        """,
        Long.class,
        receivableId,
        assignorId,
        UUID.randomUUID(),
        OffsetDateTime.ofInstant(settledAt, ZoneOffset.UTC));
  }
}
