// Entrypoint for forward-api. Boots Spring context, auto-wires config and routes.
// Entrypoint do forward-api. Inicializa contexto Spring, config e rotas automaticamente.
package com.fwdford.forwardapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ForwardApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(ForwardApiApplication.class, args);
  }
}
