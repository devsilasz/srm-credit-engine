package br.com.srm.creditengine.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.srm.creditengine.AbstractIT;
import br.com.srm.creditengine.TestData;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class StatementQueryPlanIT extends AbstractIT {

  @Autowired StatementQuery query;
  @Autowired NamedParameterJdbcTemplate named;

  @Test
  @DisplayName("AC-STM-08 com 10 mil linhas, cedente e período usam índice, sem Seq Scan")
  void usesIndexForAssignorAndPeriod() {
    Instant now = Instant.now();
    long[] assignors = {TestData.assignor(jdbc), TestData.assignor(jdbc), TestData.assignor(jdbc)};
    jdbc.update(
        """
        WITH new_receivable AS (
            INSERT INTO receivable (assignor_id, type, face_value, due_date, status, version, created_at)
            SELECT (ARRAY[?, ?, ?]::bigint[])[1 + g % 3], 'DUPLICATA_MERCANTIL', 100000.00,
                   DATE '2030-01-01', 'SETTLED', 1, now()
            FROM generate_series(1, 10000) g
            RETURNING id, assignor_id
        )
        INSERT INTO settlement (receivable_id, assignor_id, receivable_type, face_value, term_months,
            base_rate_monthly, spread_monthly, present_value_brl, discount, payment_currency,
            present_value_payment, idempotency_key, request_hash, settled_at)
        SELECT id, assignor_id, 'DUPLICATA_MERCANTIL', 100000.00, 3, 0.01, 0.015, 92859.94, 7140.06,
               'BRL', 92859.94, gen_random_uuid(), 'hash', now() - random() * interval '365 days'
        FROM new_receivable
        """,
        assignors[0], assignors[1], assignors[2]);
    jdbc.execute("ANALYZE settlement");

    LocalDate today = LocalDate.ofInstant(now, ZoneId.of("America/Sao_Paulo"));
    var filter =
        new StatementQuery.Filter(
            today.minusDays(7), today, assignors[0], null, 0, 20, "settledAt", false);

    assertThat(plan(query.itemsSql(filter), filter)).doesNotContain("Seq Scan on settlement");
    assertThat(plan(query.countSql(filter), filter)).doesNotContain("Seq Scan on settlement");
  }

  private String plan(String sql, StatementQuery.Filter filter) {
    List<String> lines =
        named.queryForList("EXPLAIN " + sql, query.parameters(filter), String.class);
    return String.join("\n", lines);
  }
}
