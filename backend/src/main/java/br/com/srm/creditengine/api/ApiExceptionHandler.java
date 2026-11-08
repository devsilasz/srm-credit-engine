package br.com.srm.creditengine.api;

import br.com.srm.creditengine.application.FxRateService;
import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Todo erro da API sai como {@code ProblemDetail} (RFC 9457), com um {@code code} estável. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private static final String VALIDATION_ERROR = "VALIDATION_ERROR";

  record FieldProblem(String field, String message) {}

  @ExceptionHandler(BusinessRuleException.class)
  ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleException e) {
    HttpStatus status = statusOf(e.code());
    ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
    if (e.code() == ErrorCode.FX_PROVIDER_UNAVAILABLE) {
      response.header(
          HttpHeaders.RETRY_AFTER, String.valueOf(FxRateService.RETRY_AFTER.toSeconds()));
    }
    return response.body(problem(status, e.code().name(), e.getMessage()));
  }

  /** Sem mensagem interna no corpo: o detalhe vai só para o log, com stack trace. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
    log.error("Erro inesperado", e);
    return ResponseEntity.internalServerError()
        .body(
            problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Erro interno. A operação não foi concluída; nada foi gravado."));
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldProblem> errors =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldProblem(error.getField(), error.getDefaultMessage()))
            .toList();
    return handleExceptionInternal(e, validationProblem(errors), headers, status, request);
  }

  /** Validação de parâmetros de query, como o par de câmbio. */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldProblem> errors =
        e.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            error ->
                                new FieldProblem(
                                    result.getMethodParameter().getParameterName(),
                                    error.getDefaultMessage())))
            .toList();
    return handleExceptionInternal(e, validationProblem(errors), headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    List<FieldProblem> errors = List.of(new FieldProblem(e.getPropertyName(), "valor inválido"));
    return handleExceptionInternal(e, validationProblem(errors), headers, status, request);
  }

  /**
   * O corpo não vira o DTO. A mensagem é montada aqui para não expor o texto interno do Jackson.
   */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException e,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "O corpo da requisição é inválido.");
    if (e.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
      String field =
          mapping.getPath().stream()
              .map(JsonMappingException.Reference::getFieldName)
              .collect(Collectors.joining("."));
      String message = messageFor(mapping);
      problem = validationProblem(List.of(new FieldProblem(field, message)));
      problem.setDetail("Campo `" + field + "`: " + message + ".");
    }
    return handleExceptionInternal(e, problem, headers, status, request);
  }

  private static String messageFor(JsonMappingException mapping) {
    if (mapping instanceof InvalidFormatException format && format.getTargetType().isEnum()) {
      String accepted =
          Arrays.stream(format.getTargetType().getEnumConstants())
              .map(Object::toString)
              .collect(Collectors.joining(", "));
      return "valor não aceito; use um de: " + accepted;
    }
    return "valor inválido";
  }

  /** Único ponto de saída dos erros do framework (405, 415, rota inexistente...). */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(e, body, headers, status, request);
    if (response != null
        && response.getBody() instanceof ProblemDetail problem
        && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
      String code = HttpStatus.valueOf(status.value()).name();
      problem.setType(typeOf(code));
      problem.setProperty("code", code);
    }
    return response;
  }

  /** Exaustivo de propósito: um {@code ErrorCode} novo sem status não compila. */
  private static HttpStatus statusOf(ErrorCode code) {
    return switch (code) {
      case ASSIGNOR_NOT_FOUND, RECEIVABLE_NOT_FOUND, SETTLEMENT_NOT_FOUND, FX_RATE_NOT_FOUND ->
          HttpStatus.NOT_FOUND;
      case RECEIVABLE_ALREADY_SETTLED -> HttpStatus.CONFLICT;
      case IDEMPOTENCY_KEY_REQUIRED, INVALID_FILTER -> HttpStatus.BAD_REQUEST;
      case IDEMPOTENCY_KEY_REUSED,
              DUE_DATE_NOT_IN_FUTURE,
              DUE_DATE_TOO_FAR,
              PRESENT_VALUE_TOO_SMALL,
              INVALID_FX_RATE ->
          HttpStatus.UNPROCESSABLE_ENTITY;
      case FX_RATE_UNAVAILABLE, FX_PROVIDER_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
    };
  }

  private static ProblemDetail validationProblem(List<FieldProblem> errors) {
    ProblemDetail problem =
        problem(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, "Há campos inválidos na requisição.");
    problem.setProperty("errors", errors);
    return problem;
  }

  static ProblemDetail problem(HttpStatus status, String code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(typeOf(code));
    problem.setProperty("code", code);
    return problem;
  }

  private static URI typeOf(String code) {
    return URI.create(
        "https://srm.example/errors/" + code.toLowerCase(Locale.ROOT).replace('_', '-'));
  }
}
