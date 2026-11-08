package br.com.srm.creditengine.api;

import br.com.srm.creditengine.application.SettlementService;
import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import br.com.srm.creditengine.infrastructure.persistence.Settlement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {

  private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

  // UUID.fromString aceita formas como "1-1-1-1-1", que duas vezes seriam chaves "diferentes".
  private static final Pattern UUID_FORMAT =
      Pattern.compile("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");

  private final SettlementService settlements;

  public SettlementController(SettlementService settlements) {
    this.settlements = settlements;
  }

  @PostMapping
  public ResponseEntity<SettlementResponse> settle(
      @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
      @Valid @RequestBody SettleRequest request) {
    SettlementService.Outcome outcome =
        settlements.settle(
            parseKey(idempotencyKey), request.receivableId(), request.paymentCurrency());
    SettlementResponse body = SettlementResponse.of(outcome.settlement());
    if (outcome.replayed()) {
      return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(body);
    }
    return ResponseEntity.created(URI.create("/api/settlements/" + body.id())).body(body);
  }

  @GetMapping("/{id}")
  public SettlementResponse get(@PathVariable Long id) {
    return SettlementResponse.of(settlements.get(id));
  }

  private static UUID parseKey(String idempotencyKey) {
    if (idempotencyKey == null || !UUID_FORMAT.matcher(idempotencyKey).matches()) {
      throw new BusinessRuleException(
          ErrorCode.IDEMPOTENCY_KEY_REQUIRED, "O header Idempotency-Key precisa ser um UUID.");
    }
    return UUID.fromString(idempotencyKey);
  }

  /** Só identifica o que liquidar: os valores são sempre recalculados no servidor. */
  public record SettleRequest(
      @NotNull Long receivableId, @NotNull PaymentCurrency paymentCurrency) {}

  /**
   * @param presentValue PV na moeda de pagamento
   * @param discount deságio, sempre em BRL
   */
  public record SettlementResponse(
      Long id,
      Long receivableId,
      Long assignorId,
      ReceivableType type,
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
      UUID idempotencyKey,
      Instant settledAt) {

    static SettlementResponse of(Settlement settlement) {
      return new SettlementResponse(
          settlement.getId(),
          settlement.getReceivableId(),
          settlement.getAssignorId(),
          settlement.getReceivableType(),
          settlement.getFaceValue(),
          settlement.getTermMonths(),
          settlement.getBaseRateMonthly(),
          settlement.getSpreadMonthly(),
          settlement.getPresentValueBrl(),
          settlement.getDiscount(),
          settlement.getPaymentCurrency(),
          settlement.getPresentValuePayment(),
          settlement.getFxRateId(),
          settlement.getFxRate(),
          settlement.getIdempotencyKey(),
          settlement.getSettledAt());
    }
  }
}
