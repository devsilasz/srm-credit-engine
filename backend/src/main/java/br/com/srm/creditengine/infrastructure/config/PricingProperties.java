package br.com.srm.creditengine.infrastructure.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param baseRateMonthly fração: 1% = 0.01. Acima de 0.5 é quase certo erro de unidade, e a
 *     aplicação recusa subir (bug nº 1 do Anexo A).
 */
@Validated
@ConfigurationProperties("pricing")
public record PricingProperties(
    @NotNull @DecimalMin("0") @DecimalMax("0.5") BigDecimal baseRateMonthly) {}
