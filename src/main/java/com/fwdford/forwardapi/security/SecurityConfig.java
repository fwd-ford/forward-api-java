// Spring Security configuration. Disables session, CSRF (we use tokens),
// allows open health and WSDL, requires auth on every other path.
// Configuracao do Spring Security: sem sessao, sem CSRF (usamos tokens).
package com.fwdford.forwardapi.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, AuthFilter authFilter)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .formLogin(f -> f.disable())
        .httpBasic(b -> b.disable())
        // Wire the CorsFilter bean into the security chain so it runs BEFORE auth.
        // Without this the bean is appended after AuthFilter and preflight OPTIONS gets 401.
        // Conecta o CorsFilter bean ao chain antes da auth; sem isso, OPTIONS toma 401.
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/health", "/ready", "/actuator/**")
                    .permitAll()
                    .requestMatchers("/soap/vehicles.wsdl")
                    .permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .permitAll())
        .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
