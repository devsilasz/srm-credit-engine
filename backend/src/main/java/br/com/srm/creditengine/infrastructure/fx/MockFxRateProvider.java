package br.com.srm.creditengine.infrastructure.fx;

import br.com.srm.creditengine.application.FxRateProvider;
import br.com.srm.creditengine.infrastructure.config.FxProperties;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Configurável por {@code fx.mock.*} para demonstrar lentidão e queda do provedor. */
@Component
public class MockFxRateProvider implements FxRateProvider {

  private final FxProperties.Mock mock;

  public MockFxRateProvider(FxProperties properties) {
    this.mock = properties.mock();
  }

  @Override
  public BigDecimal fetchRate(String pair) {
    try {
      Thread.sleep(mock.delay());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Chamada ao provedor interrompida", e);
    }
    if (mock.fail()) {
      throw new IllegalStateException("Provedor de câmbio mockado configurado para falhar");
    }
    return mock.rate();
  }
}
