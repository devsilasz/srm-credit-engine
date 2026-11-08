package br.com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.srm.creditengine.AbstractIT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@ExtendWith(OutputCaptureExtension.class)
class ApiErrorsIT extends AbstractIT {

  @LocalServerPort int port;
  @Autowired ObjectMapper objectMapper;

  private ResponseEntity<JsonNode> quote(Map<String, Object> body) {
    return rest.postForEntity("/api/pricing/quote", body, JsonNode.class);
  }

  private Map<String, Object> validQuote(String faceValue) {
    return Map.of(
        "faceValue",
        faceValue,
        "dueDate",
        today().plusMonths(3).toString(),
        "type",
        "DUPLICATA_MERCANTIL",
        "paymentCurrency",
        "BRL");
  }

  private ResponseEntity<JsonNode> postRawJson(String path, String json) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return rest.postForEntity(path, new HttpEntity<>(json, headers), JsonNode.class);
  }

  private static void assertIsProblem(ResponseEntity<JsonNode> response, HttpStatus status) {
    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getHeaders().getContentType().toString())
        .isEqualTo("application/problem+json");
    JsonNode body = response.getBody();
    assertThat(body.get("type").asText()).startsWith("https://srm.example/errors/");
    assertThat(body.get("title").asText()).isNotBlank();
    assertThat(body.get("status").asInt()).isEqualTo(status.value());
    assertThat(body.get("detail").asText()).isNotBlank();
    assertThat(body.get("code").asText()).isNotBlank();
  }

  @Test
  @DisplayName("AC-API-01 erro de negócio, validação e framework saem todos como ProblemDetail")
  void everyErrorIsAProblemDetail() {
    assertIsProblem(
        rest.getForEntity("/api/receivables/999999", JsonNode.class), HttpStatus.NOT_FOUND);
    assertIsProblem(quote(validQuote("-1.00")), HttpStatus.BAD_REQUEST);
    assertIsProblem(postRawJson("/api/pricing/quote", "{not json"), HttpStatus.BAD_REQUEST);
    assertIsProblem(
        rest.getForEntity("/api/receivables/abc", JsonNode.class), HttpStatus.BAD_REQUEST);
    assertIsProblem(rest.getForEntity("/api/nada", JsonNode.class), HttpStatus.NOT_FOUND);
    assertIsProblem(
        rest.exchange("/api/pricing/quote", HttpMethod.GET, null, JsonNode.class),
        HttpStatus.METHOD_NOT_ALLOWED);
    HttpHeaders text = new HttpHeaders();
    text.setContentType(MediaType.TEXT_PLAIN);
    assertIsProblem(
        rest.postForEntity("/api/pricing/quote", new HttpEntity<>("oi", text), JsonNode.class),
        HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    assertIsProblem(
        rest.getForEntity("/api/fx-rates/current?pair=EUR-BRL", JsonNode.class),
        HttpStatus.BAD_REQUEST);
    assertIsProblem(
        rest.getForEntity("/api/fx-rates/current", JsonNode.class), HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("AC-API-02 erro inesperado: 500 genérico no corpo e stack trace só no log")
  void unexpectedErrorHidesInternals(CapturedOutput output) {
    long receivable = openReceivable();
    jdbc.execute(
        """
        CREATE FUNCTION fail_secret() RETURNS TRIGGER AS $$
        BEGIN RAISE EXCEPTION 'detalhe-interno-sql'; END;
        $$ LANGUAGE plpgsql
        """);
    jdbc.execute(
        """
        CREATE TRIGGER fail_secret BEFORE INSERT ON settlement
        FOR EACH ROW EXECUTE FUNCTION fail_secret()
        """);
    try {
      ResponseEntity<String> response =
          rest.exchange(
              "/api/settlements",
              HttpMethod.POST,
              new HttpEntity<>(
                  Map.of("receivableId", receivable, "paymentCurrency", "BRL"), keyHeader()),
              String.class);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(response.getBody())
          .doesNotContain("detalhe-interno-sql")
          .doesNotContain("Exception")
          .doesNotContain("settlement (");
      assertThat(output).contains("Erro inesperado").contains("detalhe-interno-sql");
    } finally {
      jdbc.execute("DROP TRIGGER fail_secret ON settlement");
      jdbc.execute("DROP FUNCTION fail_secret()");
    }
  }

  private static HttpHeaders keyHeader() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Idempotency-Key", UUID.randomUUID().toString());
    return headers;
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1.00", "100.001", "1e400"})
  @DisplayName("AC-PRC-11 face menor ou igual a zero, ou com 3+ casas, é 400 em errors[].field")
  void invalidFaceValueIs400(String faceValue) {
    ResponseEntity<JsonNode> response = quote(validQuote(faceValue));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(response.getBody().get("errors").findValuesAsText("field")).contains("faceValue");
  }

  @Test
  void missingFieldsAreListed() {
    ResponseEntity<JsonNode> response = quote(Map.of());

    List<String> fields = response.getBody().get("errors").findValuesAsText("field");
    assertThat(fields).containsExactlyInAnyOrder("faceValue", "dueDate", "type", "paymentCurrency");
  }

  @Test
  @DisplayName("AC-PRC-12 tipo desconhecido é 400 e lista os tipos aceitos")
  void unknownTypeListsAcceptedValues() {
    String json =
        """
        {"faceValue": "100.00", "dueDate": "2030-01-01",
         "type": "NOTA_PROMISSORIA", "paymentCurrency": "BRL"}
        """;

    ResponseEntity<JsonNode> response = postRawJson("/api/pricing/quote", json);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("errors").findValuesAsText("field")).contains("type");
    assertThat(response.getBody().get("detail").asText())
        .contains("DUPLICATA_MERCANTIL")
        .contains("CHEQUE_PRE_DATADO");
  }

  @Test
  void malformedDateDoesNotLeakParserMessage() {
    String json =
        """
        {"faceValue": "100.00", "dueDate": "amanhã",
         "type": "DUPLICATA_MERCANTIL", "paymentCurrency": "BRL"}
        """;

    ResponseEntity<JsonNode> response = postRawJson("/api/pricing/quote", json);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().get("errors").findValuesAsText("field")).contains("dueDate");
    assertThat(response.getBody().toString()).doesNotContain("java.time");
  }

  @Test
  @DisplayName("AC-SEC-03 corpo acima de 64 KB com Content-Length é 413")
  void oversizedBodyIs413() {
    String body = "{\"x\":\"" + "a".repeat(PayloadLimitFilter.MAX_BYTES) + "\"}";

    assertIsProblem(postRawJson("/api/pricing/quote", body), HttpStatus.PAYLOAD_TOO_LARGE);
  }

  @Test
  @DisplayName("AC-SEC-03 corpo chunked acima de 64 KB, sem Content-Length, também é 413")
  void oversizedChunkedBodyIs413() throws Exception {
    byte[] body = new byte[PayloadLimitFilter.MAX_BYTES + 1];
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/pricing/quote"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)))
            .build();

    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(413);
      assertThat(objectMapper.readTree(response.body()).get("code").asText())
          .isEqualTo("PAYLOAD_TOO_LARGE");
    }
  }

  @Test
  void bodyWithinLimitStillReachesController() {
    assertThat(quote(validQuote("100000.00")).getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
