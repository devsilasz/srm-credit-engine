package br.com.srm.creditengine.api;

import br.com.srm.creditengine.infrastructure.persistence.Assignor;
import br.com.srm.creditengine.infrastructure.persistence.AssignorRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Leitura sem regra de negócio: um serviço aqui só repassaria a chamada. */
@Tag(name = "Cedentes", description = "Empresas que cedem os recebíveis")
@RestController
@RequestMapping("/api/assignors")
public class AssignorController {

  private final AssignorRepository assignors;

  public AssignorController(AssignorRepository assignors) {
    this.assignors = assignors;
  }

  @Operation(summary = "Lista os cedentes")
  @ApiResponse(responseCode = "200", description = "Cedentes em ordem alfabética")
  @GetMapping
  public List<AssignorResponse> list() {
    return assignors.findAll(Sort.by("name")).stream().map(AssignorResponse::of).toList();
  }

  public record AssignorResponse(Long id, String name, String document) {

    static AssignorResponse of(Assignor assignor) {
      return new AssignorResponse(assignor.getId(), assignor.getName(), assignor.getDocument());
    }
  }
}
