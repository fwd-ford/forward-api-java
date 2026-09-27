// Clock bean so time-dependent code (JWT issuing and validation) is testable.
// Bean de Clock para que codigo dependente de tempo seja testavel.
package com.fwdford.forwardapi.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
