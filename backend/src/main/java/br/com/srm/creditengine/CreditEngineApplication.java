package br.com.srm.creditengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CreditEngineApplication {

  public static void main(String[] args) {
    SpringApplication.run(CreditEngineApplication.class, args);
  }
}
