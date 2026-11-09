package br.com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import br.com.srm.creditengine.AbstractIT;
import br.com.srm.creditengine.TestData;
import br.com.srm.creditengine.application.FxRateProvider;
import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.SimpleValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * AC-API-05: requisições reais validadas contra o OpenAPI publicado pela própria aplicação. O
 * provedor é mock porque um cenário precisa dele fora; os demais usam uma taxa inserida direto no
 * banco.
 */
class ContractIT extends AbstractIT {

  /** Status por operação: a tabela da skill {@code api-conventions}. */
  private static final Map<String, Set<String>> EXPECTED_STATUSES =
      Map.ofEntries(
          Map.entry("post /api/pricing/quote", Set.of("200", "400", "422", "503")),
          Map.entry("post /api/receivables", Set.of("201", "400", "404", "422")),
          Map.entry("get /api/receivables/{id}", Set.of("200", "404")),
          Map.entry(
              "post /api/settlements", Set.of("200", "201", "400", "404", "409", "422", "503")),
          Map.entry("get /api/settlements/{id}", Set.of("200", "404")),
          Map.entry("get /api/settlements", Set.of("200", "400")),
          Map.entry("post /api/fx-rates", Set.of("201", "400", "422")),
          Map.entry("post /api/fx-rates/refresh", Set.of("201", "503")),
          Map.entry("get /api/fx-rates/current", Set.of("200", "404")),
          Map.entry("get /api/assignors", Set.of("200")));

  private static final Set<String> EXERCISED = new TreeSet<>();

  @MockitoBean FxRateProvider provider;
  @Autowired CircuitBreaker fxProviderCircuitBreaker;
  @Autowired ObjectMapper objectMapper;

  private OpenApiInteractionValidator validator;

  @BeforeEach
  void loadPublishedContract() {
    reset(provider);
    fxProviderCircuitBreaker.reset();
    validator =
        OpenApiInteractionValidator.createForInlineApiSpecification(publishedSpec()).build();
  }

  private String publishedSpec() {
    return rest.getForObject("/v3/api-docs", String.class);
  }

  /** Valida pedido e resposta. Para pedidos que respeitam o contrato. */
  private void assertConforms(
      HttpMethod method, String path, String query, Object body, HttpHeaders headers, int status) {
    ResponseEntity<String> response = exchange(method, path, query, body, headers);
    assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);

    SimpleRequest.Builder request =
        new SimpleRequest.Builder(method.name(), path).withContentType("application/json");
    if (query != null) {
      for (String pair : query.split("&")) {
        String[] parts = pair.split("=", 2);
        request.withQueryParam(parts[0], parts[1]);
      }
    }
    if (body != null) {
      request.withBody(toJson(body));
    }
    headers.forEach((name, values) -> request.withHeader(name, values));
    assertNoViolations(validator.validate(request.build(), simpleResponse(response)));
  }

  /** Só a resposta: para pedidos que violam o contrato de propósito, como faltar o header. */
  private void assertResponseConforms(
      HttpMethod method, String path, String query, Object body, HttpHeaders headers, int status) {
    ResponseEntity<String> response = exchange(method, path, query, body, headers);
    assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);

    Request.Method validatorMethod = Request.Method.valueOf(method.name());
    assertNoViolations(validator.validateResponse(path, validatorMethod, simpleResponse(response)));
  }

  private ResponseEntity<String> exchange(
      HttpMethod method, String path, String query, Object body, HttpHeaders headers) {
    String url = query == null ? path : path + "?" + query;
    ResponseEntity<String> response =
        rest.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    String operation =
        method.name().toLowerCase(Locale.ROOT) + " " + path.replaceAll("/\\d+", "/{id}");
    EXERCISED.add(operation + "," + response.getStatusCode().value());
    return response;
  }

  /**
   * Evidência para docs/coverage: cada combinação de operação e status, declarada ou exercitada.
   */
  @AfterAll
  static void writeOperationCoverage() throws IOException {
    Set<String> all = new TreeSet<>(EXERCISED);
    EXPECTED_STATUSES.forEach(
        (operation, statuses) -> statuses.forEach(status -> all.add(operation + "," + status)));
    List<String> lines = new ArrayList<>(List.of("operation,status,declared,exercised"));
    for (String entry : all) {
      String[] parts = entry.split(",");
      boolean declared = EXPECTED_STATUSES.getOrDefault(parts[0], Set.of()).contains(parts[1]);
      lines.add(entry + "," + declared + "," + EXERCISED.contains(entry));
    }
    Files.write(Path.of("target/contract-operations.csv"), lines);
  }

  private static SimpleResponse simpleResponse(ResponseEntity<String> response) {
    SimpleResponse.Builder builder =
        SimpleResponse.Builder.status(response.getStatusCode().value());
    response.getHeaders().forEach(builder::withHeader);
    if (response.getBody() != null) {
      builder.withBody(response.getBody());
    }
    return builder.build();
  }

  private String toJson(Object body) {
    try {
      return objectMapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static void assertNoViolations(ValidationReport report) {
    assertThat(report.hasErrors())
        .as(() -> SimpleValidationReportFormat.getInstance().apply(report))
        .isFalse();
  }

  private static HttpHeaders withKey(String key) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Idempotency-Key", key);
    return headers;
  }

  private Map<String, Object> quoteBody(String dueDate, String currency) {
    return Map.of(
        "faceValue",
        "100000.00",
        "dueDate",
        dueDate,
        "type",
        "DUPLICATA_MERCANTIL",
        "paymentCurrency",
        currency);
  }

  private static Map<String, Object> settleBody(long receivableId, String currency) {
    return Map.of("receivableId", receivableId, "paymentCurrency", currency);
  }

  @Test
  void quote() {
    TestData.fxRate(jdbc, "5.4321", clock.instant());
    String inThreeMonths = today().plusMonths(3).toString();

    assertConforms(
        HttpMethod.POST,
        "/api/pricing/quote",
        null,
        quoteBody(inThreeMonths, "BRL"),
        new HttpHeaders(),
        200);
    assertConforms(
        HttpMethod.POST,
        "/api/pricing/quote",
        null,
        quoteBody(inThreeMonths, "USD"),
        new HttpHeaders(),
        200);
    assertConforms(
        HttpMethod.POST,
        "/api/pricing/quote",
        null,
        quoteBody(today().toString(), "BRL"),
        new HttpHeaders(),
        422);
    assertResponseConforms(
        HttpMethod.POST,
        "/api/pricing/quote",
        null,
        Map.of("faceValue", "-1", "dueDate", inThreeMonths, "type", "X", "paymentCurrency", "BRL"),
        new HttpHeaders(),
        400);
  }

  @Test
  void receivables() {
    Map<String, Object> body =
        Map.of(
            "assignorId",
            TestData.assignor(jdbc),
            "type",
            "DUPLICATA_MERCANTIL",
            "faceValue",
            "100000.00",
            "dueDate",
            today().plusMonths(3).toString());

    assertConforms(HttpMethod.POST, "/api/receivables", null, body, new HttpHeaders(), 201);
    assertConforms(
        HttpMethod.GET, "/api/receivables/" + openReceivable(), null, null, new HttpHeaders(), 200);
    assertConforms(HttpMethod.GET, "/api/receivables/999999", null, null, new HttpHeaders(), 404);
  }

  @Test
  void settlements() {
    long receivable = openReceivable();
    String key = UUID.randomUUID().toString();
    Object body = settleBody(receivable, "BRL");

    assertConforms(HttpMethod.POST, "/api/settlements", null, body, withKey(key), 201);
    assertConforms(HttpMethod.POST, "/api/settlements", null, body, withKey(key), 200);
    assertResponseConforms(HttpMethod.POST, "/api/settlements", null, body, new HttpHeaders(), 400);
    assertConforms(
        HttpMethod.POST,
        "/api/settlements",
        null,
        settleBody(999999, "BRL"),
        withKey(UUID.randomUUID().toString()),
        404);
    assertConforms(
        HttpMethod.POST,
        "/api/settlements",
        null,
        body,
        withKey(UUID.randomUUID().toString()),
        409);
    assertConforms(
        HttpMethod.POST,
        "/api/settlements",
        null,
        settleBody(openReceivable(), "BRL"),
        withKey(key),
        422);

    long id =
        rest.exchange(
                "/api/settlements",
                HttpMethod.POST,
                new HttpEntity<>(body, withKey(key)),
                JsonNode.class)
            .getBody()
            .get("id")
            .asLong();
    assertConforms(HttpMethod.GET, "/api/settlements/" + id, null, null, new HttpHeaders(), 200);
    assertConforms(HttpMethod.GET, "/api/settlements/999999", null, null, new HttpHeaders(), 404);
  }

  @Test
  void settlementInUsdCarriesFxFields() {
    TestData.fxRate(jdbc, "5.4321", clock.instant());

    assertConforms(
        HttpMethod.POST,
        "/api/settlements",
        null,
        settleBody(openReceivable(), "USD"),
        withKey(UUID.randomUUID().toString()),
        201);
  }

  @Test
  void statement() {
    long assignor = TestData.assignor(jdbc);
    TestData.settlement(jdbc, assignor, clock.instant());
    TestData.usdSettlement(jdbc, assignor, clock.instant());

    assertConforms(
        HttpMethod.GET,
        "/api/settlements",
        "assignorId=" + assignor + "&page=0&size=10&sort=settledAt,desc",
        null,
        new HttpHeaders(),
        200);
    assertConforms(
        HttpMethod.GET,
        "/api/settlements",
        "from=2026-03-11&to=2026-03-10",
        null,
        new HttpHeaders(),
        400);
  }

  @Test
  void fxRates() {
    Map<String, Object> body =
        Map.of("pair", "USD-BRL", "rate", "5.43210000", "validFrom", clock.instant().toString());

    assertConforms(HttpMethod.POST, "/api/fx-rates", null, body, new HttpHeaders(), 201);
    assertConforms(
        HttpMethod.GET, "/api/fx-rates/current", "pair=USD-BRL", null, new HttpHeaders(), 200);
    assertConforms(
        HttpMethod.POST,
        "/api/fx-rates",
        null,
        Map.of("pair", "USD-BRL", "rate", "0", "validFrom", clock.instant().toString()),
        new HttpHeaders(),
        422);

    doThrow(new IllegalStateException("provedor fora")).when(provider).fetchRate(anyString());
    assertConforms(HttpMethod.POST, "/api/fx-rates/refresh", null, null, new HttpHeaders(), 503);
  }

  @Test
  void assignors() {
    assertConforms(HttpMethod.GET, "/api/assignors", null, null, new HttpHeaders(), 200);
  }

  @Test
  @DisplayName("AC-API-03 toda operação declara os status da tabela de convenções")
  void everyOperationDocumentsItsStatuses() throws Exception {
    JsonNode paths = objectMapper.readTree(publishedSpec()).get("paths");

    EXPECTED_STATUSES.forEach(
        (operation, expected) -> {
          String[] parts = operation.split(" ");
          JsonNode responses = paths.path(parts[1]).path(parts[0]).path("responses");
          Set<String> declared = new TreeSet<>();
          responses.fieldNames().forEachRemaining(declared::add);
          assertThat(declared).as(operation).containsAll(expected);
        });
  }

  @Test
  @DisplayName("AC-SET-11 não existe PUT, PATCH nem DELETE em /api/settlements")
  void settlementsCannotBeChangedThroughTheApi() throws Exception {
    JsonNode paths = objectMapper.readTree(publishedSpec()).get("paths");

    for (String path : List.of("/api/settlements", "/api/settlements/{id}")) {
      assertThat(paths.has(path)).as(path).isTrue();
      for (String method : List.of("put", "patch", "delete")) {
        assertThat(paths.get(path).has(method)).as(method + " " + path).isFalse();
      }
    }
  }

  /** Controle negativo: prova que o validador lê o corpo, em vez de passar em qualquer resposta. */
  @Test
  void validatorRejectsMoneyAsJsonNumber() {
    SimpleResponse moneyAsNumber =
        SimpleResponse.Builder.status(200)
            .withContentType("application/json")
            .withBody("{\"presentValue\": 92859.94}")
            .build();

    assertThat(
            validator
                .validateResponse("/api/pricing/quote", Request.Method.POST, moneyAsNumber)
                .hasErrors())
        .isTrue();
  }

  @Test
  void swaggerUiIsPublished() {
    assertThat(rest.getForEntity("/swagger-ui/index.html", String.class).getStatusCode().value())
        .isEqualTo(200);
  }
}
