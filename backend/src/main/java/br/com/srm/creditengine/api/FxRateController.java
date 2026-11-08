package br.com.srm.creditengine.api;

import br.com.srm.creditengine.application.FxRateService;
import br.com.srm.creditengine.domain.model.FxRateSource;
import br.com.srm.creditengine.infrastructure.persistence.FxRate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fx-rates")
@Validated
public class FxRateController {

  private static final String SUPPORTED_PAIR = "USD-BRL";

  private final FxRateService fxRates;

  public FxRateController(FxRateService fxRates) {
    this.fxRates = fxRates;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public FxRateResponse create(@Valid @RequestBody CreateFxRateRequest request) {
    return FxRateResponse.of(
        fxRates.createManual(request.pair(), request.rate(), request.validFrom()));
  }

  @PostMapping("/refresh")
  @ResponseStatus(HttpStatus.CREATED)
  public FxRateResponse refresh() {
    return FxRateResponse.of(fxRates.refresh());
  }

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
