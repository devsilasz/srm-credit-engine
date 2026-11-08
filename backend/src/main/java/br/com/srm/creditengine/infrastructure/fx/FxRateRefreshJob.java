package br.com.srm.creditengine.infrastructure.fx;

import br.com.srm.creditengine.application.FxRateService;
import br.com.srm.creditengine.domain.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mantém {@code fx_rate} atualizada: uma vez na subida e depois em intervalo fixo. Falha do
 * provedor não derruba a aplicação: BRL segue normal, e USD recusa quando a taxa vencer.
 */
@Component
public class FxRateRefreshJob implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(FxRateRefreshJob.class);

  private final FxRateService fxRates;

  public FxRateRefreshJob(FxRateService fxRates) {
    this.fxRates = fxRates;
  }

  @Override
  public void run(ApplicationArguments args) {
    refresh();
  }

  @Scheduled(
      fixedDelayString = "${fx.refresh-interval}",
      initialDelayString = "${fx.refresh-interval}")
  public void refresh() {
    try {
      fxRates.refresh();
    } catch (BusinessRuleException e) {
      log.warn("Refresh de câmbio falhou: {}", e.getMessage(), e);
    }
  }
}
