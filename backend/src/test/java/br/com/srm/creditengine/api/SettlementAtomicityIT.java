package br.com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.srm.creditengine.AbstractIT;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * A falha é injetada no banco, por um trigger só deste teste, para exercitar o código real sem
 * nenhum gancho de teste em produção.
 */
class SettlementAtomicityIT extends AbstractIT {

  private void failSettlementInsertsFor(long receivable, String sqlState) {
    jdbc.execute(
        """
        CREATE FUNCTION fail_settlement_insert() RETURNS TRIGGER AS $$
        BEGIN
          IF NEW.receivable_id = %d THEN
            RAISE EXCEPTION 'falha forçada' USING ERRCODE = '%s';
          END IF;
          RETURN NEW;
        END;
        $$ LANGUAGE plpgsql
        """
            .formatted(receivable, sqlState));
    jdbc.execute(
        """
        CREATE TRIGGER fail_settlement_insert BEFORE INSERT ON settlement
        FOR EACH ROW EXECUTE FUNCTION fail_settlement_insert()
        """);
  }

  @AfterEach
  void removeFailure() {
    jdbc.execute("DROP TRIGGER IF EXISTS fail_settlement_insert ON settlement");
    jdbc.execute("DROP FUNCTION IF EXISTS fail_settlement_insert()");
  }

  private void assertNothingChanged(long receivable) {
    assertThat(settlementCount(receivable)).isZero();
    assertThat(receivableStatus(receivable)).isEqualTo("OPEN");
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM receivable WHERE id = ?", Long.class, receivable))
        .isZero();
  }

  @Test
  @DisplayName("AC-SET-09 falha entre as duas escritas: rollback total e 500, nunca 2xx")
  void failureBetweenWritesRollsBackEverything() {
    long receivable = openReceivable();
    failSettlementInsertsFor(receivable, "P0001");

    ResponseEntity<JsonNode> response = settle(UUID.randomUUID().toString(), receivable, "BRL");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().get("code").asText()).isEqualTo("INTERNAL_ERROR");
    assertNothingChanged(receivable);
  }

  @Test
  void otherConstraintViolationIsABugNotAConflict() {
    long receivable = openReceivable();
    failSettlementInsertsFor(receivable, "23514");

    ResponseEntity<JsonNode> response = settle(UUID.randomUUID().toString(), receivable, "BRL");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertNothingChanged(receivable);
  }

  @Test
  void afterTheFailureTheReceivableCanBeSettled() {
    long receivable = openReceivable();
    String key = UUID.randomUUID().toString();
    failSettlementInsertsFor(receivable, "P0001");
    settle(key, receivable, "BRL");
    removeFailure();

    assertThat(settle(key, receivable, "BRL").getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }
}
