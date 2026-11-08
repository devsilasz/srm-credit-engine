package br.com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.srm.creditengine.AbstractIT;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Repetido porque um único acerto em teste de concorrência pode ser só sorte de escalonamento. */
class SettlementConcurrencyIT extends AbstractIT {

  private List<ResponseEntity<JsonNode>> settleTogether(
      long receivable, String firstKey, String secondKey) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
      Future<ResponseEntity<JsonNode>> first =
          threads.submit(
              () -> {
                start.await();
                return settle(firstKey, receivable, "BRL");
              });
      Future<ResponseEntity<JsonNode>> second =
          threads.submit(
              () -> {
                start.await();
                return settle(secondKey, receivable, "BRL");
              });
      start.countDown();
      return List.of(first.get(), second.get());
    }
  }

  private static List<HttpStatus> statuses(List<ResponseEntity<JsonNode>> responses) {
    return responses.stream().map(r -> HttpStatus.valueOf(r.getStatusCode().value())).toList();
  }

  @RepeatedTest(5)
  @DisplayName("AC-SET-07 chaves diferentes no mesmo recebível: um 201 e um 409")
  void differentKeysOneWins() throws Exception {
    long receivable = openReceivable();

    var responses =
        settleTogether(receivable, UUID.randomUUID().toString(), UUID.randomUUID().toString());

    assertThat(statuses(responses))
        .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
    assertThat(settlementCount(receivable)).isEqualTo(1);
    assertThat(receivableStatus(receivable)).isEqualTo("SETTLED");
  }

  @RepeatedTest(5)
  @DisplayName("AC-SET-08 mesma chave em paralelo: um 201 e um 200 com o mesmo id")
  void sameKeyBothGetTheSameSettlement() throws Exception {
    long receivable = openReceivable();
    String key = UUID.randomUUID().toString();

    var responses = settleTogether(receivable, key, key);

    assertThat(statuses(responses)).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.OK);
    assertThat(responses.get(0).getBody().get("id"))
        .isEqualTo(responses.get(1).getBody().get("id"));
    assertThat(settlementCount(receivable)).isEqualTo(1);
  }
}
