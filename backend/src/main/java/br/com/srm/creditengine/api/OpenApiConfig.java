package br.com.srm.creditengine.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String PROBLEM = "Problem";
  private static final String PROBLEM_JSON = "application/problem+json";

  static {
    // No JSON, BigDecimal é sempre string (ver ApplicationConfig). O contrato precisa dizer o
    // mesmo.
    SpringDocUtils.getConfig()
        .replaceWithSchema(BigDecimal.class, new StringSchema().example("92859.94"));
  }

  @Bean
  OpenAPI creditEngineOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("SRM Credit Engine API")
                .version("v1")
                .description("Precificação e liquidação de recebíveis em BRL e USD."));
  }

  /** O formato de erro é único, então é declarado aqui, e não em cada {@code @ApiResponse}. */
  @Bean
  OpenApiCustomizer problemResponses() {
    return openApi -> {
      // Registrado aqui porque o springdoc descarta os components do bean OpenAPI ao montar o spec.
      openApi.getComponents().addSchemas(PROBLEM, problemSchema());
      openApi
          .getPaths()
          .values()
          .forEach(
              path ->
                  path.readOperationsMap()
                      .forEach(
                          (method, operation) -> {
                            if (method == PathItem.HttpMethod.POST) {
                              operation
                                  .getResponses()
                                  .addApiResponse(
                                      "413", new ApiResponse().description("Corpo acima de 64 KB"));
                            }
                            operation
                                .getResponses()
                                .addApiResponse(
                                    "500", new ApiResponse().description("Erro interno"));
                            operation.getResponses().forEach(OpenApiConfig::describeProblem);
                          }));
    };
  }

  private static void describeProblem(String status, ApiResponse response) {
    if (status.compareTo("400") >= 0) {
      Schema<?> reference = new Schema<>().$ref("#/components/schemas/" + PROBLEM);
      response.setContent(
          new Content().addMediaType(PROBLEM_JSON, new MediaType().schema(reference)));
    }
  }

  private static Schema<?> problemSchema() {
    Schema<?> fieldError =
        new ObjectSchema()
            .addProperty("field", new StringSchema())
            .addProperty("message", new StringSchema());
    return new ObjectSchema()
        .addProperty(
            "type", new StringSchema().example("https://srm.example/errors/receivable-not-found"))
        .addProperty("title", new StringSchema().example("Not Found"))
        .addProperty("status", new IntegerSchema().example(404))
        .addProperty("detail", new StringSchema().example("Recebível 42 não encontrado."))
        .addProperty("instance", new StringSchema().example("/api/receivables/42"))
        .addProperty("code", new StringSchema().example("RECEIVABLE_NOT_FOUND"))
        .addProperty("errors", new ArraySchema().items(fieldError))
        .required(List.of("type", "title", "status", "detail", "code"))
        .example(
            Map.of(
                "type", "https://srm.example/errors/receivable-not-found",
                "title", "Not Found",
                "status", 404,
                "detail", "Recebível 42 não encontrado.",
                "instance", "/api/receivables/42",
                "code", "RECEIVABLE_NOT_FOUND"));
  }
}
