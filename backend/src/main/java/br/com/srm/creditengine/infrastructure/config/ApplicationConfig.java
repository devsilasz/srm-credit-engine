package br.com.srm.creditengine.infrastructure.config;

import br.com.srm.creditengine.domain.pricing.PricingEngine;
import br.com.srm.creditengine.domain.pricing.PricingStrategy;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableScheduling
// O domínio não conhece Spring: as estratégias entram aqui por tipo, sem @Component. Um tipo novo
// de recebível só precisa da classe e do valor no enum.
@ComponentScan(
    basePackageClasses = PricingStrategy.class,
    useDefaultFilters = false,
    includeFilters =
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PricingStrategy.class))
public class ApplicationConfig {

  /** {@code LocalDate.now(clock)} usa o fuso do negócio; {@code clock.instant()} segue em UTC. */
  @Bean
  Clock clock() {
    return Clock.system(ZoneId.of("America/Sao_Paulo"));
  }

  @Bean
  PricingEngine pricingEngine(List<PricingStrategy> strategies, PricingProperties properties) {
    return new PricingEngine(strategies, properties.baseRateMonthly());
  }

  /** Só para o Vite em desenvolvimento. No compose, o Nginx faz proxy e não há CORS. */
  @Bean
  WebMvcConfigurer viteDevCors() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/api/**")
            .allowedOrigins("http://localhost:5173")
            .allowedMethods("GET", "POST")
            .exposedHeaders("Location", "Idempotent-Replayed", "Retry-After");
      }
    };
  }

  /** Dinheiro e taxa trafegam como string: número JSON perde precisão no cliente. */
  @Bean
  Jackson2ObjectMapperBuilderCustomizer bigDecimalAsPlainString() {
    return builder ->
        builder.serializerByType(
            BigDecimal.class,
            new JsonSerializer<BigDecimal>() {
              @Override
              public void serialize(
                  BigDecimal value, JsonGenerator generator, SerializerProvider provider)
                  throws IOException {
                generator.writeString(value.toPlainString());
              }
            });
  }
}
