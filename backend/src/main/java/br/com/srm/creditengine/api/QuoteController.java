package br.com.srm.creditengine.api;

import br.com.srm.creditengine.application.Quote;
import br.com.srm.creditengine.application.QuoteService;
import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import br.com.srm.creditengine.domain.pricing.PricingResult;
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

@RestController
@RequestMapping("/api/pricing")
public class QuoteController {

  private final QuoteService quotes;

  public QuoteController(QuoteService quotes) {
    this.quotes = quotes;
  }

  @PostMapping("/quote")
  public QuoteResponse quote(@Valid @RequestBody QuoteRequest request) {
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
