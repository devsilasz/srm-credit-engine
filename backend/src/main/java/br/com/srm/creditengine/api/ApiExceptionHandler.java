package br.com.srm.creditengine.api;

import br.com.srm.creditengine.application.FxRateService;
import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import java.net.URI;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Todo erro da API sai como {@code ProblemDetail} (RFC 9457), com um {@code code} estável. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

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

  private static ProblemDetail problem(HttpStatus status, String code, String detail) {
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
