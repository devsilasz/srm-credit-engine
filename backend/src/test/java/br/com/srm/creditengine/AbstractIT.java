package br.com.srm.creditengine;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Base dos testes de integração: um único Postgres real, compartilhado entre os contextos. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability(tracing = false)
@Import(AbstractIT.ClockConfig.class)
public abstract class AbstractIT {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  @Autowired protected MutableClock clock;
  @Autowired protected TestRestTemplate rest;
  @Autowired protected JdbcTemplate jdbc;

  @AfterEach
  void resetClock() {
    clock.reset();
  }

  protected LocalDate today() {
    return LocalDate.now(clock);
  }

  /** Recebível C1: duplicata de 100000.00 vencendo em 3 meses. */
  protected long openReceivable() {
    return openReceivable(TestData.assignor(jdbc), today().plusMonths(3));
  }

  protected long openReceivable(long assignorId, LocalDate dueDate) {
    Map<String, Object> body =
        Map.of(
            "assignorId",
            assignorId,
            "type",
            "DUPLICATA_MERCANTIL",
            "faceValue",
            "100000.00",
            "dueDate",
            dueDate.toString());
    return rest.postForObject("/api/receivables", body, JsonNode.class).get("id").asLong();
  }

  protected ResponseEntity<JsonNode> settle(String key, long receivableId, String currency) {
    HttpHeaders headers = new HttpHeaders();
    if (key != null) {
      headers.set("Idempotency-Key", key);
    }
    Map<String, Object> body = Map.of("receivableId", receivableId, "paymentCurrency", currency);
    return rest.postForEntity("/api/settlements", new HttpEntity<>(body, headers), JsonNode.class);
  }

  protected long settlementCount(long receivableId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM settlement WHERE receivable_id = ?", Long.class, receivableId);
  }

  protected String receivableStatus(long receivableId) {
    return jdbc.queryForObject(
        "SELECT status FROM receivable WHERE id = ?", String.class, receivableId);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class ClockConfig {
    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock();
    }
  }
}
