// Spring Security configuration: stateless bearer-token API.
//
// Filter order (servlet level):
//   RequestIdFilter -> SecurityHeadersFilter -> RateLimitFilter -> Spring Security chain
//   (CORS -> JwtAuthenticationFilter -> ExceptionTranslation -> AuthorizationFilter)
//
// Public endpoints are listed explicitly; everything else requires authentication.
// Role checks and dealer scoping live in the service layer (@PreAuthorize + programmatic
// checks), see docs/ARQUITETURA.md for the permission matrix.
//
// CSRF is disabled on purpose: CSRF abuses credentials the browser attaches
// automatically (cookies, HTTP auth). This API keeps no session and no cookies; every
// request must carry an explicit Authorization header (or X-API-Key), which a cross-site
// form or image tag cannot forge.
//
// Configuracao do Spring Security: API stateless com Bearer JWT. CSRF desabilitado
// porque nao ha sessao nem cookies; cada request traz o header Authorization.
package com.fwdford.forwardapi.security;

import com.fwdford.forwardapi.config.AppProperties;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  /** GET endpoints reachable without a token. */
  private static final String[] PUBLIC_GET = {
    "/health",
    "/ready",
    "/actuator/health",
    "/actuator/health/**",
    "/v3/api-docs",
    "/v3/api-docs/**",
    "/v3/api-docs.yaml",
    "/swagger-ui.html",
    "/swagger-ui/**",
    "/soap/vehicles.wsdl"
  };

  /** POST endpoints reachable without a token. */
  private static final String[] PUBLIC_POST = {"/api/v1/auth/login"};

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      JwtService jwtService,
      AppProperties props,
      CorsConfigurationSource corsSource,
      ProblemAuthenticationEntryPoint entryPoint,
      ProblemAccessDeniedHandler accessDeniedHandler)
      throws Exception {
    JwtAuthenticationFilter jwtFilter =
        new JwtAuthenticationFilter(jwtService, props.internalApiKey());

    http.cors(c -> c.configurationSource(corsSource))
        .csrf(csrf -> csrf.disable())
        .formLogin(f -> f.disable())
        .httpBasic(b -> b.disable())
        .logout(l -> l.disable())
        .requestCache(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, PUBLIC_POST)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET)
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /** BCrypt (cost 10) for app_users.password_hash. */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
  }
}
