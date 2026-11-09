package br.com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.srm.creditengine.AbstractIT;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReceivableApiIT extends AbstractIT {

  private Map<String, Object> body(long assignorId, String dueDate) {
    return Map.of(
        "assignorId",
        assignorId,
        "type",
        "CHEQUE_PRE_DATADO",
        "faceValue",
        "25000",
        "dueDate",
        dueDate);
  }

  @Test
  void createsOpenReceivableWithNormalizedScale() {
    var response =
        rest.postForEntity(
            "/api/receivables", body(1, today().plusMonths(2).toString()), JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long id = response.getBody().get("id").asLong();
    assertThat(response.getHeaders().getLocation()).hasPath("/api/receivables/" + id);
    assertThat(response.getBody().get("status").asText()).isEqualTo("OPEN");
    assertThat(response.getBody().get("faceValue").asText()).isEqualTo("25000.00");
    assertThat(rest.getForObject("/api/receivables/" + id, JsonNode.class))
        .isEqualTo(response.getBody());
  }

  @Test
  void unknownAssignorIs404() {
    var response =
        rest.postForEntity(
            "/api/receivables", body(999999, today().plusMonths(2).toString()), JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().get("code").asText()).isEqualTo("ASSIGNOR_NOT_FOUND");
  }

  @Test
  void invalidBodyIs400AndCreatesNothing() {
    long before = jdbc.queryForObject("SELECT count(*) FROM receivable", Long.class);
    Map<String, Object> body =
        Map.of(
            "assignorId",
            1,
            "type",
            "CHEQUE_PRE_DATADO",
            "faceValue",
            "-1.00",
            "dueDate",
            today().plusMonths(2).toString());

    var response = rest.postForEntity("/api/receivables", body, JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("errors").findValuesAsText("field")).contains("faceValue");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM receivable", Long.class))
        .isEqualTo(before);
  }

  @Test
  void dueDateNotInFutureIs422() {
    var response =
        rest.postForEntity("/api/receivables", body(1, today().toString()), JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody().get("code").asText()).isEqualTo("DUE_DATE_NOT_IN_FUTURE");
  }

  @Test
  void unknownReceivableIs404() {
    var response = rest.getForEntity("/api/receivables/999999", JsonNode.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().get("code").asText()).isEqualTo("RECEIVABLE_NOT_FOUND");
  }

  @Test
  void listsSeededAssignors() {
    JsonNode assignors = rest.getForObject("/api/assignors", JsonNode.class);

    assertThat(assignors.findValuesAsText("name")).contains("Metalúrgica Aurora Ltda");
  }
}
