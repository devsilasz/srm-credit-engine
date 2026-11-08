package br.com.srm.creditengine.api;

import br.com.srm.creditengine.application.ReceivableService;
import br.com.srm.creditengine.domain.model.ReceivableStatus;
import br.com.srm.creditengine.domain.model.ReceivableType;
import br.com.srm.creditengine.infrastructure.persistence.Receivable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Recebíveis", description = "Cadastro mínimo do título a liquidar")
@RestController
@RequestMapping("/api/receivables")
public class ReceivableController {

  private final ReceivableService receivables;

  public ReceivableController(ReceivableService receivables) {
    this.receivables = receivables;
  }

  @Operation(summary = "Cadastra um recebível em aberto")
  @ApiResponse(responseCode = "201", description = "Recebível criado")
  @ApiResponse(responseCode = "400", description = "Pedido malformado ou inválido")
  @ApiResponse(responseCode = "404", description = "Cedente não encontrado")
  @ApiResponse(responseCode = "422", description = "Vencimento recusado pela regra de negócio")
  @PostMapping
  public ResponseEntity<ReceivableResponse> create(
      @Valid @RequestBody CreateReceivableRequest request) {
    Receivable receivable =
        receivables.create(
            request.assignorId(), request.type(), request.faceValue(), request.dueDate());
    return ResponseEntity.created(URI.create("/api/receivables/" + receivable.getId()))
        .body(ReceivableResponse.of(receivable));
  }

  @Operation(summary = "Consulta um recebível")
  @ApiResponse(responseCode = "200", description = "Recebível encontrado")
  @ApiResponse(responseCode = "400", description = "Id malformado")
  @ApiResponse(responseCode = "404", description = "Recebível não encontrado")
  @GetMapping("/{id}")
  public ReceivableResponse get(@PathVariable Long id) {
    return ReceivableResponse.of(receivables.get(id));
  }

  public record CreateReceivableRequest(
      @NotNull Long assignorId,
      @NotNull ReceivableType type,
      @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal faceValue,
      @NotNull LocalDate dueDate) {}

  public record ReceivableResponse(
      Long id,
      Long assignorId,
      ReceivableType type,
      BigDecimal faceValue,
      LocalDate dueDate,
      ReceivableStatus status,
      Instant createdAt) {

    static ReceivableResponse of(Receivable receivable) {
      return new ReceivableResponse(
          receivable.getId(),
          receivable.getAssignorId(),
          receivable.getType(),
          receivable.getFaceValue(),
          receivable.getDueDate(),
          receivable.getStatus(),
          receivable.getCreatedAt());
    }
  }
}
