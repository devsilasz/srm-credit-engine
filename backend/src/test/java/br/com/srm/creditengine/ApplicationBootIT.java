package br.com.srm.creditengine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

class ApplicationBootIT extends AbstractIT {

  @Autowired TestRestTemplate rest;

  @Test
  void bootsAgainstRealPostgresAndReportsHealthUp() {
    var health = rest.getForEntity("/actuator/health", String.class);

    assertThat(health.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(health.getBody()).contains("\"status\":\"UP\"");
  }
}
