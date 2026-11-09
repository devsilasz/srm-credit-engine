package br.com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.srm.creditengine.application.FxRateService;
import br.com.srm.creditengine.application.Quote;
import br.com.srm.creditengine.application.QuoteService;
import br.com.srm.creditengine.application.ReceivableService;
import br.com.srm.creditengine.application.SettlementService;
import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import br.com.srm.creditengine.domain.model.FxRateSource;
import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import br.com.srm.creditengine.domain.pricing.PricingResult;
import br.com.srm.creditengine.infrastructure.persistence.Assignor;
import br.com.srm.creditengine.infrastructure.persistence.AssignorRepository;
import br.com.srm.creditengine.infrastructure.persistence.FxRate;
import br.com.srm.creditengine.infrastructure.persistence.Receivable;
import br.com.srm.creditengine.infrastructure.persistence.Settlement;
import br.com.srm.creditengine.infrastructure.persistence.StatementQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Camada HTTP sem Spring Boot nem banco: rotas, validação e o mapa de erro para status. */
class ApiWebLayerTest {

  private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
  private static final String KEY = "6f1c2a34-1111-4222-8333-444455556666";

  private final QuoteService quotes = mock(QuoteService.class);
  private final ReceivableService receivables = mock(ReceivableService.class);
  private final SettlementService settlements = mock(SettlementService.class);
  private final FxRateService fxRates = mock(FxRateService.class);
  private final AssignorRepository assignors = mock(AssignorRepository.class);
  private final StatementQuery statement = mock(StatementQuery.class);
  private final ApiExceptionHandler handler = new ApiExceptionHandler();

  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(
              new QuoteController(quotes),
              new ReceivableController(receivables),
              new SettlementController(settlements),
              new FxRateController(fxRates),
              new AssignorController(assignors),
              new StatementController(statement))
          .setControllerAdvice(handler)
          .build();

  private static PricingResult pricing(PaymentCurrency currency, String presentValue) {
    return new PricingResult(
        new BigDecimal("100000.00"),
        3,
        new BigDecimal("0.010000"),
        new BigDecimal("0.015000"),
        new BigDecimal("92859.94"),
        new BigDecimal("7140.06"),
        currency,
        new BigDecimal(presentValue));
  }

  private static FxRate rate() {
    FxRate rate =
        new FxRate("USD-BRL", new BigDecimal("5.43210000"), NOW, FxRateSource.MANUAL, NOW);
    ReflectionTestUtils.setField(rate, "id", 11L);
    return rate;
  }

  private static Settlement settlement() {
    Receivable receivable =
        new Receivable(
            1L,
            ReceivableType.DUPLICATA_MERCANTIL,
            new BigDecimal("100000.00"),
            LocalDate.parse("2026-04-15"),
            NOW);
    ReflectionTestUtils.setField(receivable, "id", 7L);
    Settlement settlement =
        new Settlement(
            receivable,
            pricing(PaymentCurrency.BRL, "92859.94"),
            null,
            UUID.fromString(KEY),
            "hash",
            NOW);
    ReflectionTestUtils.setField(settlement, "id", 42L);
    return settlement;
  }

  private static String settleBody() {
    return "{\"receivableId\":7,\"paymentCurrency\":\"BRL\"}";
  }

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  @DisplayName("Todo ErrorCode vira ProblemDetail com o status da tabela de convenções")
  void everyErrorCodeHasItsStatus(ErrorCode code) {
    Set<ErrorCode> notFound =
        EnumSet.of(
            ErrorCode.ASSIGNOR_NOT_FOUND,
            ErrorCode.RECEIVABLE_NOT_FOUND,
            ErrorCode.SETTLEMENT_NOT_FOUND,
            ErrorCode.FX_RATE_NOT_FOUND);
    Set<ErrorCode> badRequest =
        EnumSet.of(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, ErrorCode.INVALID_FILTER);
    Set<ErrorCode> unavailable =
        EnumSet.of(ErrorCode.FX_RATE_UNAVAILABLE, ErrorCode.FX_PROVIDER_UNAVAILABLE);
    HttpStatus expected;
    if (notFound.contains(code)) {
      expected = HttpStatus.NOT_FOUND;
    } else if (badRequest.contains(code)) {
      expected = HttpStatus.BAD_REQUEST;
    } else if (unavailable.contains(code)) {
      expected = HttpStatus.SERVICE_UNAVAILABLE;
    } else if (code == ErrorCode.RECEIVABLE_ALREADY_SETTLED) {
      expected = HttpStatus.CONFLICT;
    } else {
      expected = HttpStatus.UNPROCESSABLE_ENTITY;
    }

    var response = handler.handleBusinessRule(new BusinessRuleException(code, "detalhe"));

    assertThat(response.getStatusCode()).isEqualTo(expected);
    ProblemDetail body = response.getBody();
    assertThat(body.getDetail()).isEqualTo("detalhe");
    assertThat(body.getProperties()).containsEntry("code", code.name());
    assertThat(body.getType().toString()).startsWith("https://srm.example/errors/");
    assertThat(response.getHeaders().containsKey(HttpHeaders.RETRY_AFTER))
        .isEqualTo(code == ErrorCode.FX_PROVIDER_UNAVAILABLE);
  }

  @Test
  @DisplayName("Erro inesperado não expõe a mensagem interna")
  void unexpectedErrorHidesTheMessage() {
    var response = handler.handleUnexpected(new IllegalStateException("segredo interno"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().getDetail()).doesNotContain("segredo");
  }

  @Test
  void quoteInBrlHasNoFxFields() throws Exception {
    when(quotes.quote(any(), any(), any(), any()))
        .thenReturn(new Quote(pricing(PaymentCurrency.BRL, "92859.94"), null));

    mvc.perform(
            post("/api/pricing/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"faceValue\":\"100000.00\",\"dueDate\":\"2026-04-15\","
                        + "\"type\":\"DUPLICATA_MERCANTIL\",\"paymentCurrency\":\"BRL\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.termMonths").value(3))
        .andExpect(jsonPath("$.fxRateId").doesNotExist());
  }

  @Test
  void quoteInUsdCarriesTheRateUsed() throws Exception {
    when(quotes.quote(any(), any(), any(), any()))
        .thenReturn(new Quote(pricing(PaymentCurrency.USD, "17094.67"), rate()));

    mvc.perform(
            post("/api/pricing/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"faceValue\":\"100000.00\",\"dueDate\":\"2026-04-15\","
                        + "\"type\":\"DUPLICATA_MERCANTIL\",\"paymentCurrency\":\"USD\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fxRateId").value(11));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "{\"faceValue\":\"-1\",\"dueDate\":\"2026-04-15\",\"type\":\"CHEQUE_PRE_DATADO\",\"paymentCurrency\":\"BRL\"} | VALIDATION_ERROR",
        "{\"faceValue\":\"1\",\"dueDate\":\"2026-04-15\",\"type\":\"OUTRO\",\"paymentCurrency\":\"BRL\"}               | VALIDATION_ERROR",
        "{\"faceValue\":\"1\",\"dueDate\":\"amanhã\",\"type\":\"CHEQUE_PRE_DATADO\",\"paymentCurrency\":\"BRL\"}       | VALIDATION_ERROR",
        "{não é json                                                                                                  | MALFORMED_REQUEST",
      })
  void invalidBodyIs400WithItsCode(String body, String code) throws Exception {
    mvc.perform(post("/api/pricing/quote").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(code));
  }

  @Test
  void unknownEnumListsTheAcceptedValues() throws Exception {
    mvc.perform(
            post("/api/pricing/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"faceValue\":\"1\",\"dueDate\":\"2026-04-15\",\"type\":\"OUTRO\","
                        + "\"paymentCurrency\":\"BRL\"}"))
        .andExpect(jsonPath("$.errors[0].field").value("type"))
        .andExpect(jsonPath("$.detail").value(containsString("DUPLICATA_MERCANTIL")));
  }

  @Test
  void frameworkErrorsAlsoCarryACode() throws Exception {
    mvc.perform(get("/api/pricing/quote"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    mvc.perform(post("/api/pricing/quote").contentType(MediaType.TEXT_PLAIN).content("oi"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    mvc.perform(get("/api/fx-rates/current"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void pathAndQueryValidationNameTheField() throws Exception {
    mvc.perform(get("/api/receivables/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("id"));
    mvc.perform(get("/api/fx-rates/current").param("pair", "EUR-BRL"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("pair"));
  }

  @Test
  void newSettlementIs201WithLocation() throws Exception {
    when(settlements.settle(any(), any(), any()))
        .thenReturn(new SettlementService.Outcome(settlement(), false));

    mvc.perform(
            post("/api/settlements")
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(settleBody()))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/settlements/42"))
        .andExpect(header().doesNotExist("Idempotent-Replayed"))
        .andExpect(jsonPath("$.id").value(42));
  }

  @Test
  void replayIs200WithHeader() throws Exception {
    when(settlements.settle(any(), any(), any()))
        .thenReturn(new SettlementService.Outcome(settlement(), true));

    mvc.perform(
            post("/api/settlements")
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(settleBody()))
        .andExpect(status().isOk())
        .andExpect(header().string("Idempotent-Replayed", "true"));
  }

  @ParameterizedTest
  @CsvSource({"abc", "1-1-1-1-1", "6f1c2a34-1111-4222-8333-44445555666"})
  @DisplayName("Chave fora do formato canônico é 400: UUID.fromString aceitaria algumas delas")
  void malformedKeyIsRefused(String key) throws Exception {
    mvc.perform(
            post("/api/settlements")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(settleBody()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
  }

  @Test
  void missingKeyIsRefused() throws Exception {
    mvc.perform(
            post("/api/settlements").contentType(MediaType.APPLICATION_JSON).content(settleBody()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
  }

  @Test
  void getSettlementReturnsIt() throws Exception {
    when(settlements.get(42L)).thenReturn(settlement());

    mvc.perform(get("/api/settlements/42"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.receivableId").value(7));
  }

  @Test
  void createsAndReadsReceivable() throws Exception {
    Receivable receivable =
        new Receivable(
            1L,
            ReceivableType.CHEQUE_PRE_DATADO,
            new BigDecimal("25000.00"),
            LocalDate.parse("2026-03-15"),
            NOW);
    ReflectionTestUtils.setField(receivable, "id", 7L);
    when(receivables.create(any(), any(), any(), any())).thenReturn(receivable);
    when(receivables.get(7L)).thenReturn(receivable);

    mvc.perform(
            post("/api/receivables")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"assignorId\":1,\"type\":\"CHEQUE_PRE_DATADO\",\"faceValue\":\"25000\","
                        + "\"dueDate\":\"2026-03-15\"}"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/receivables/7"));
    mvc.perform(get("/api/receivables/7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"));
  }

  @Test
  void fxEndpointsReturnTheRate() throws Exception {
    when(fxRates.createManual(any(), any(), any())).thenReturn(rate());
    when(fxRates.refresh()).thenReturn(rate());
    when(fxRates.current("USD-BRL")).thenReturn(rate());

    mvc.perform(
            post("/api/fx-rates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"pair\":\"USD-BRL\",\"rate\":\"5.4321\","
                        + "\"validFrom\":\"2026-01-15T12:00:00Z\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(11));
    mvc.perform(post("/api/fx-rates/refresh")).andExpect(status().isCreated());
    mvc.perform(get("/api/fx-rates/current").param("pair", "USD-BRL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pair").value("USD-BRL"));
  }

  @Test
  void assignorsComeInAlphabeticalOrder() throws Exception {
    Assignor assignor = mock(Assignor.class);
    when(assignor.getId()).thenReturn(1L);
    when(assignor.getName()).thenReturn("Cedente");
    when(assignor.getDocument()).thenReturn("11111111000111");
    when(assignors.findAll(Sort.by("name"))).thenReturn(List.of(assignor));

    mvc.perform(get("/api/assignors"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Cedente"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "from=2026-03-11&to=2026-03-10",
        "page=-1",
        "size=0",
        "size=101",
        "sort=amount",
        "sort=settledAt;desc",
        "sort=settledAt,lado",
        "sort=settledAt,desc,asc",
      })
  @DisplayName("AC-STM-05 e AC-STM-06 filtro inválido é recusado antes de chegar ao SQL")
  void invalidFiltersAreRefused(String query) throws Exception {
    mvc.perform(get("/api/settlements?" + query))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
    verifyNoInteractions(statement);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {"30 | 10 | 3", "31 | 10 | 4", "0 | 10 | 0"})
  @DisplayName("totalPages arredonda para cima")
  void totalPagesRoundsUp(long total, int size, int expectedPages) throws Exception {
    when(statement.find(any())).thenReturn(new StatementQuery.Result(List.of(), total));

    mvc.perform(get("/api/settlements").param("size", String.valueOf(size)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalPages").value(expectedPages))
        .andExpect(jsonPath("$.totalElements").value(total));
  }

  @Test
  void filterIsPassedToTheQuery() throws Exception {
    when(statement.find(any())).thenReturn(new StatementQuery.Result(List.of(), 0));
    var captor = ArgumentCaptor.forClass(StatementQuery.Filter.class);

    mvc.perform(
            get("/api/settlements")
                .param("from", "2026-03-10")
                .param("to", "2026-03-11")
                .param("assignorId", "5")
                .param("currency", "USD")
                .param("page", "2")
                .param("sort", "faceValue,asc"))
        .andExpect(status().isOk());

    verify(statement).find(captor.capture());
    StatementQuery.Filter filter = captor.getValue();
    assertThat(filter.assignorId()).isEqualTo(5L);
    assertThat(filter.currency()).isEqualTo(PaymentCurrency.USD);
    assertThat(filter.page()).isEqualTo(2);
    assertThat(filter.sortField()).isEqualTo("faceValue");
    assertThat(filter.ascending()).isTrue();
  }
}
