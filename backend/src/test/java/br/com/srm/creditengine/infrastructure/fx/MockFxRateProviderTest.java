package br.com.srm.creditengine.infrastructure.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.srm.creditengine.infrastructure.config.FxProperties;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MockFxRateProviderTest {

  private static MockFxRateProvider provider(boolean fail) {
    var mock = new FxProperties.Mock(new BigDecimal("5.4321"), Duration.ZERO, fail);
    return new MockFxRateProvider(
        new FxProperties(Duration.ofHours(1), Duration.ofSeconds(2), mock));
  }

  @Test
  void returnsConfiguredRate() {
    assertThat(provider(false).fetchRate("USD-BRL")).isEqualByComparingTo("5.4321");
  }

  @Test
  void interruptedCallFailsAndKeepsTheInterruptFlag() {
    var slow = new FxProperties.Mock(new BigDecimal("5.4321"), Duration.ofSeconds(30), false);
    var provider =
        new MockFxRateProvider(new FxProperties(Duration.ofHours(1), Duration.ofSeconds(2), slow));
    Thread.currentThread().interrupt();

    assertThatThrownBy(() -> provider.fetchRate("USD-BRL"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(Thread.interrupted()).isTrue();
  }

  @Test
  void failsWhenConfiguredTo() {
    assertThatThrownBy(() -> provider(true).fetchRate("USD-BRL"))
        .isInstanceOf(IllegalStateException.class);
  }
}
