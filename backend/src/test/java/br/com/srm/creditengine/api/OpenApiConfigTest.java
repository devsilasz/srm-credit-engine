package br.com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

  private final OpenApiConfig config = new OpenApiConfig();

  private static Operation operation(String... statuses) {
    ApiResponses responses = new ApiResponses();
    for (String status : statuses) {
      responses.addApiResponse(status, new ApiResponse().description(status));
    }
    return new Operation().responses(responses);
  }

  private OpenAPI customized() {
    OpenAPI openApi =
        new OpenAPI()
            .components(new Components())
            .paths(
                new Paths()
                    .addPathItem("/escrita", new PathItem().post(operation("201", "404")))
                    .addPathItem("/leitura", new PathItem().get(operation("200"))));
    config.problemResponses().customise(openApi);
    return openApi;
  }

  @Test
  @DisplayName("Regressão: o schema Problem precisa estar no spec, ou todo erro aponta para o nada")
  void registersTheProblemSchema() {
    assertThat(customized().getComponents().getSchemas()).containsKey("Problem");
  }

  @Test
  void errorResponsesGetTheProblemBodyAndSuccessKeepsItsOwn() {
    Operation write = customized().getPaths().get("/escrita").getPost();

    assertThat(write.getResponses().get("404").getContent())
        .containsKey("application/problem+json");
    assertThat(write.getResponses().get("201").getContent()).isNull();
  }

  @Test
  void payloadLimitIsDocumentedOnlyWhereThereIsABody() {
    OpenAPI openApi = customized();

    assertThat(openApi.getPaths().get("/escrita").getPost().getResponses())
        .containsKeys("413", "500");
    assertThat(openApi.getPaths().get("/leitura").getGet().getResponses())
        .containsKey("500")
        .doesNotContainKey("413");
  }

  @Test
  void publishesTitle() {
    assertThat(config.creditEngineOpenApi().getInfo().getTitle())
        .isEqualTo("SRM Credit Engine API");
  }
}
