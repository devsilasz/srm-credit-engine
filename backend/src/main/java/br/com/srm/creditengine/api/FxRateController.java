package br.com.srm.creditengine.api;

import br.com.srm.creditengine.application.FxRateService;
import br.com.srm.creditengine.domain.model.FxRateSource;
import br.com.srm.creditengine.infrastructure.persistence.FxRate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Câmbio", description = "Histórico append-only de taxas em BRL por 1 USD")
@RestController
@RequestMapping("/api/fx-rates")
public class FxRateController {

  private static final String SUPPORTED_PAIR = "USD-BRL";

  private final FxRateService fxRates;

  public FxRateController(FxRateService fxRates) {
    this.fxRates = fxRates;
  }

  @Operation(
      summary = "Registra uma taxa manual",
      description = "Sempre insere uma linha nova: o histórico nunca é alterado.")
  @ApiResponse(responseCode = "201", description = "Taxa registrada")
  @ApiResponse(responseCode = "400", description = "Pedido malformado ou mais de 8 casas decimais")
  @ApiResponse(responseCode = "422", description = "Taxa menor ou igual a zero")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public FxRateResponse create(@Valid @RequestBody CreateFxRateRequest request) {
    return FxRateResponse.of(
        fxRates.createManual(request.pair(), request.rate(), request.validFrom()));
  }

  @Operation(
      summary = "Busca a taxa no provedor",
      description = "Timeout de 2 s por tentativa, 3 tentativas com backoff e circuit breaker.")
  @ApiResponse(responseCode = "201", description = "Taxa do provedor registrada")
  @ApiResponse(
      responseCode = "503",
      description = "Provedor indisponível ou circuito aberto",
      headers = @Header(name = "Retry-After", description = "Segundos até tentar de novo"))
  @PostMapping("/refresh")
  @ResponseStatus(HttpStatus.CREATED)
  public FxRateResponse refresh() {
    return FxRateResponse.of(fxRates.refresh());
  }

  @Operation(summary = "Consulta a taxa vigente")
  @ApiResponse(responseCode = "200", description = "Taxa vigente")
  @ApiResponse(responseCode = "400", description = "Par não suportado")
  @ApiResponse(responseCode = "404", description = "Sem taxa vigente")
  @GetMapping("/current")
  public FxRateResponse current(
      @RequestParam @Pattern(regexp = SUPPORTED_PAIR, message = "par não suportado") String pair) {
    return FxRateResponse.of(fxRates.current(pair));
  }

  public record CreateFxRateRequest(
      @NotNull @Pattern(regexp = SUPPORTED_PAIR, message = "par não suportado") String pair,
      @NotNull @Digits(integer = 11, fraction = 8) BigDecimal rate,
      @NotNull Instant validFrom) {}

  public record FxRateResponse(
      Long id, String pair, BigDecimal rate, Instant validFrom, FxRateSource source) {

    static FxRateResponse of(FxRate rate) {
      return new FxRateResponse(
          rate.getId(), rate.getPair(), rate.getRate(), rate.getValidFrom(), rate.getSource());
    }
  }
}
