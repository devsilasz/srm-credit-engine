package br.com.srm.creditengine.api;

import br.com.srm.creditengine.application.Quote;
import br.com.srm.creditengine.application.QuoteService;
import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import br.com.srm.creditengine.domain.pricing.PricingResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Precificação", description = "Simulação do valor presente, sem persistir nada")
@RestController
@RequestMapping("/api/pricing")
public class QuoteController {

  private final QuoteService quotes;

  public QuoteController(QuoteService quotes) {
    this.quotes = quotes;
  }

  @Operation(
      summary = "Simula o valor presente de um recebível",
      description =
          "PV = Face / (1 + base + spread)^n, com HALF_EVEN a 2 casas só no resultado final de cada"
              + " moeda. Em USD, converte o PV em BRL já arredondado pela taxa vigente.")
  @ApiResponse(responseCode = "200", description = "Simulação calculada")
  @ApiResponse(responseCode = "400", description = "Pedido malformado ou inválido")
  @ApiResponse(responseCode = "422", description = "Regra de negócio recusou o pedido")
  @ApiResponse(responseCode = "503", description = "Sem taxa de câmbio vigente (só em USD)")
  @PostMapping("/quote")
  public QuoteResponse quote(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              content =
                  @Content(
                      examples =
                          @ExampleObject(
                              name = "Golden case C1 (dueDate = hoje + 3 meses)",
                              value =
                                  """
                                  {"faceValue": "100000.00", "dueDate": "2030-01-15",
                                   "type": "DUPLICATA_MERCANTIL", "paymentCurrency": "BRL"}
                                  """)))
          @Valid
          @RequestBody
          QuoteRequest request) {
    return QuoteResponse.of(
        quotes.quote(
            request.type(), request.faceValue(), request.dueDate(), request.paymentCurrency()));
  }

  public record QuoteRequest(
      @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal faceValue,
      @NotNull LocalDate dueDate,
      @NotNull ReceivableType type,
      @NotNull PaymentCurrency paymentCurrency) {}

  /**
   * @param presentValue PV na moeda de pagamento
   * @param discount deságio, sempre em BRL
   */
  public record QuoteResponse(
      BigDecimal faceValue,
      int termMonths,
      BigDecimal baseRate,
      BigDecimal spread,
      BigDecimal presentValueBrl,
      BigDecimal discount,
      PaymentCurrency paymentCurrency,
      BigDecimal presentValue,
      Long fxRateId,
      BigDecimal fxRate,
      Instant fxRateValidFrom) {

    static QuoteResponse of(Quote quote) {
      PricingResult pricing = quote.pricing();
      boolean hasFx = quote.fxRate() != null;
      return new QuoteResponse(
          pricing.faceValue(),
          pricing.termMonths(),
          pricing.baseRateMonthly(),
          pricing.spreadMonthly(),
          pricing.presentValueBrl(),
          pricing.discountBrl(),
          pricing.paymentCurrency(),
          pricing.presentValue(),
          hasFx ? quote.fxRate().getId() : null,
          hasFx ? quote.fxRate().getRate() : null,
          hasFx ? quote.fxRate().getValidFrom() : null);
    }
  }
}
