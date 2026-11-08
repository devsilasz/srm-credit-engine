package br.com.srm.creditengine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationBootIT extends AbstractIT {

  @Test
  void bootsAgainstRealPostgresAndReportsHealthUp() {
    var health = rest.getForEntity("/actuator/health", String.class);

    assertThat(health.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(health.getBody()).contains("\"status\":\"UP\"");
  }
}
