package br.com.srm.creditengine.api;

import br.com.srm.creditengine.application.SettlementService;
import br.com.srm.creditengine.domain.BusinessRuleException;
import br.com.srm.creditengine.domain.ErrorCode;
import br.com.srm.creditengine.domain.model.PaymentCurrency;
import br.com.srm.creditengine.domain.model.ReceivableType;
import br.com.srm.creditengine.infrastructure.persistence.Settlement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(
    name = "Liquidações",
    description =
        "Liquidação atômica, idempotente e imutável. Não existe PUT, PATCH nem DELETE: alterar uma"
            + " liquidação não é uma operação do sistema.")
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

  @Operation(
      summary = "Liquida um recebível",
      description =
          "Os valores são recalculados no servidor. Repetir a mesma Idempotency-Key com o mesmo"
              + " payload devolve a liquidação original, sem gravar outra.")
  @ApiResponse(
      responseCode = "201",
      description = "Liquidação criada",
      headers = @Header(name = "Location", description = "URL da liquidação"),
      content = @Content(schema = @Schema(implementation = SettlementResponse.class)))
  @ApiResponse(
      responseCode = "200",
      description = "Replay idempotente: a liquidação original",
      headers = @Header(name = "Idempotent-Replayed", description = "Sempre true"),
      content = @Content(schema = @Schema(implementation = SettlementResponse.class)))
  @ApiResponse(responseCode = "400", description = "Sem Idempotency-Key ou pedido inválido")
  @ApiResponse(responseCode = "404", description = "Recebível não encontrado")
  @ApiResponse(responseCode = "409", description = "Recebível já liquidado")
  @ApiResponse(responseCode = "422", description = "Chave reutilizada com outro payload")
  @ApiResponse(responseCode = "503", description = "Sem taxa de câmbio vigente (só em USD)")
  @PostMapping
  public ResponseEntity<SettlementResponse> settle(
      @Parameter(
              in = ParameterIn.HEADER,
              name = IDEMPOTENCY_KEY,
              required = true,
              description = "UUID gerado pelo cliente por intenção de liquidação",
              schema = @Schema(type = "string", format = "uuid"))
          @RequestHeader(name = IDEMPOTENCY_KEY, required = false)
          String idempotencyKey,
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

  @Operation(summary = "Consulta uma liquidação")
  @ApiResponse(responseCode = "200", description = "Liquidação encontrada")
  @ApiResponse(responseCode = "400", description = "Id malformado")
  @ApiResponse(responseCode = "404", description = "Liquidação não encontrada")
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
