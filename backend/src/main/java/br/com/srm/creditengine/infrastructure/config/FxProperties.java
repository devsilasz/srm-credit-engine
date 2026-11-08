package br.com.srm.creditengine.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param maxAge idade máxima da taxa vigente; acima disso a liquidação em USD é recusada
 * @param providerTimeout limite de cada tentativa de chamar o provedor
 */
@Validated
@ConfigurationProperties("fx")
public record FxProperties(
    @NotNull Duration maxAge, @NotNull Duration providerTimeout, @NotNull Mock mock) {

  public record Mock(@NotNull BigDecimal rate, @NotNull Duration delay, boolean fail) {}
}
