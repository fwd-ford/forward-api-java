package com.fwdford.forwardapi.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fwdford.forwardapi.config.AppProperties;
import com.fwdford.forwardapi.security.JwtService;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Authentication (401) and authorization (403) rules enforced over HTTP. */
class SecurityIT extends IntegrationTest {

  private String tokenWith(String secret, String issuer, String audience) {
    Instant now = Instant.now();
    return Jwts.builder()
        .id(UUID.randomUUID().toString())
        .issuer(issuer)
        .audience()
        .single(audience)
        .subject(TestData.ADMIN_ID)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(Duration.ofMinutes(10))))
        .claim("email", "admin@forward.dev")
        .claim("name", "Ana")
        .claim("role", "ADMIN")
        .signWith(
            new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"),
            Jwts.SIG.HS256)
        .compact();
  }

  // ---------------------------------------------------------------- 401

  @Test
  @DisplayName("sem token: 401 AUTH_REQUIRED com WWW-Authenticate")
  void no_token_is_401() throws Exception {
    mvc.perform(get("/api/v1/leads"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", Matchers.startsWith("Bearer")))
        .andExpect(problem(401, "AUTH_REQUIRED"));
  }

  @Test
  @DisplayName("token malformado: 401 AUTH_TOKEN_INVALID")
  void malformed_token_is_401() throws Exception {
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not.a.jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(problem(401, "AUTH_TOKEN_INVALID"));
  }

  @Test
  @DisplayName("esquema diferente de Bearer: 401 AUTH_TOKEN_INVALID")
  void non_bearer_scheme_is_401() throws Exception {
    mvc.perform(get("/api/v1/me").header("Authorization", "Basic YWRtaW46YWRtaW4="))
        .andExpect(status().isUnauthorized())
        .andExpect(problem(401, "AUTH_TOKEN_INVALID"));
  }

  @Test
  @DisplayName("token expirado: 401 AUTH_TOKEN_EXPIRED")
  void expired_token_is_401() throws Exception {
    AppProperties.Jwt cfg = props.jwt();
    JwtService past =
        new JwtService(
            cfg, false, Clock.fixed(Instant.now().minus(Duration.ofHours(3)), ZoneOffset.UTC));
    String expired = past.issue(ADMIN).token();

    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", Matchers.containsString("invalid_token")))
        .andExpect(problem(401, "AUTH_TOKEN_EXPIRED"));
  }

  @Test
  @DisplayName("assinatura inválida (outro segredo): 401")
  void bad_signature_is_401() throws Exception {
    String forged =
        tokenWith("another-secret-used-by-an-attacker-0123456789", "forward-api", "forward-app");
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + forged))
        .andExpect(status().isUnauthorized())
        .andExpect(problem(401, "AUTH_TOKEN_INVALID"));
  }

  @Test
  @DisplayName("emissor (iss) incorreto: 401")
  void wrong_issuer_is_401() throws Exception {
    String token = tokenWith(props.jwt().secret(), "https://evil.example", "forward-app");
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(problem(401, "AUTH_TOKEN_INVALID"));
  }

  @Test
  @DisplayName("audiência (aud) incorreta: 401")
  void wrong_audience_is_401() throws Exception {
    String token = tokenWith(props.jwt().secret(), "forward-api", "other-app");
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(problem(401, "AUTH_TOKEN_INVALID"));
  }

  @Test
  @DisplayName("token com segredo, iss e aud corretos é aceito")
  void correctly_signed_custom_token_is_accepted() throws Exception {
    String token = tokenWith(props.jwt().secret(), "forward-api", "forward-app");
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  @Test
  @DisplayName("X-API-Key inválida: 401 AUTH_API_KEY_INVALID")
  void invalid_api_key_is_401() throws Exception {
    mvc.perform(get("/api/v1/me").header("X-API-Key", "wrong-key"))
        .andExpect(status().isUnauthorized())
        .andExpect(problem(401, "AUTH_API_KEY_INVALID"));
  }

  // ---------------------------------------------------------------- 403 by profile

  @Test
  @DisplayName("ATENDENTE em /api/v1/users: 403 ACCESS_DENIED")
  void atendente_cannot_manage_users() throws Exception {
    mvc.perform(get("/api/v1/users").header("Authorization", bearer(ATENDENTE)))
        .andExpect(status().isForbidden())
        .andExpect(problem(403, "ACCESS_DENIED"));
  }

  @Test
  @DisplayName("GESTOR em /api/v1/users: 403 (inclusive antes de validar o corpo)")
  void gestor_cannot_create_users_even_with_invalid_body() throws Exception {
    mvc.perform(
            post("/api/v1/users")
                .header("Authorization", bearer(GESTOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(problem(403, "ACCESS_DENIED"));
  }

  @Test
  @DisplayName("GESTOR não exclui evento de serviço: 403")
  void gestor_cannot_delete_service_event() throws Exception {
    mvc.perform(
            delete("/api/v1/service-events/" + TestData.EVENT_DEALER_1)
                .header("Authorization", bearer(GESTOR)))
        .andExpect(status().isForbidden())
        .andExpect(problem(403, "ACCESS_DENIED"));
  }

  @Test
  @DisplayName("ATENDENTE não cria evento de serviço: 403")
  void atendente_cannot_create_service_event() throws Exception {
    mvc.perform(
            post("/api/v1/service-events")
                .header("Authorization", bearer(ATENDENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "vin",
                            TestData.VIN_DEALER_1,
                            "dealer_code",
                            "F0001",
                            "service_code",
                            1,
                            "maintenance_number",
                            1,
                            "service_date",
                            "2026-12-01T10:00:00-03:00",
                            "main_source",
                            "dealer_app"))))
        .andExpect(status().isForbidden())
        .andExpect(problem(403, "ACCESS_DENIED"));
  }

  // ---------------------------------------------------------------- 403 by dealer scope

  @Test
  @DisplayName("lead de outra concessionária: 403 ACCESS_OTHER_DEALER")
  void cross_dealer_lead_is_403() throws Exception {
    mvc.perform(
            get("/api/v1/leads/" + TestData.LEAD_NEW_DEALER_2)
                .header("Authorization", bearer(ATENDENTE)))
        .andExpect(status().isForbidden())
        .andExpect(problem(403, "ACCESS_OTHER_DEALER"));
  }

  @Test
  @DisplayName("cliente de outra concessionária: 403; ADMIN lê qualquer cliente")
  void cross_dealer_customer_is_403_but_admin_reads_it() throws Exception {
    mvc.perform(
            get("/api/v1/customers/" + TestData.CUSTOMER_DEALER_2)
                .header("Authorization", bearer(GESTOR)))
        .andExpect(status().isForbidden())
        .andExpect(problem(403, "ACCESS_OTHER_DEALER"));
    mvc.perform(
            get("/api/v1/customers/" + TestData.CUSTOMER_DEALER_2)
                .header("Authorization", bearer(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.full_name").value("Carlos Souza"));
  }

  @Test
  @DisplayName("cliente da própria concessionária e seu score: 200")
  void own_dealer_customer_and_score_are_readable() throws Exception {
    mvc.perform(
            get("/api/v1/customers/" + TestData.CUSTOMER_DEALER_1)
                .header("Authorization", bearer(ATENDENTE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(TestData.CUSTOMER_DEALER_1));
    mvc.perform(
            get("/api/v1/customers/" + TestData.CUSTOMER_DEALER_1 + "/score")
                .header("Authorization", bearer(ATENDENTE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.churn_probability").value(0.78))
        .andExpect(jsonPath("$.segment").value("esquecido"));
  }

  @Test
  @DisplayName("alias deprecado /api/v1/scores/{id} responde com headers Deprecation e Link")
  void deprecated_score_alias_has_headers() throws Exception {
    mvc.perform(
            get("/api/v1/scores/" + TestData.CUSTOMER_DEALER_1)
                .header("Authorization", bearer(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(header().string("Deprecation", "true"))
        .andExpect(header().string("Link", Matchers.containsString("successor-version")));
  }

  @Test
  @DisplayName("veículo de outra concessionária: 403; próprio: 200")
  void vehicle_scope() throws Exception {
    mvc.perform(
            get("/api/v1/vehicles/" + TestData.VIN_DEALER_2)
                .header("Authorization", bearer(ATENDENTE)))
        .andExpect(status().isForbidden())
        .andExpect(problem(403, "ACCESS_OTHER_DEALER"));
    mvc.perform(
            get("/api/v1/vehicles/" + TestData.VIN_DEALER_1)
                .header("Authorization", bearer(ATENDENTE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model").value("Ka"))
        .andExpect(jsonPath("$.current_dealer_id").value(TestData.DEALER_1));
  }

  @Test
  @DisplayName("veículo inexistente: 404 VEHICLE_NOT_FOUND")
  void unknown_vehicle_is_404() throws Exception {
    mvc.perform(
            get("/api/v1/vehicles/" + TestData.VIN_UNKNOWN).header("Authorization", bearer(ADMIN)))
        .andExpect(status().isNotFound())
        .andExpect(problem(404, "VEHICLE_NOT_FOUND"));
  }

  // ---------------------------------------------------------------- filters and headers

  @Test
  @DisplayName("respostas de erro também carregam os headers de segurança")
  void security_headers_on_401() throws Exception {
    mvc.perform(get("/api/v1/leads"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().exists("Content-Security-Policy"))
        .andExpect(header().exists("X-Request-Id"));
  }

  @Test
  @DisplayName("preflight CORS de origem permitida responde sem exigir token")
  void cors_preflight_from_allowed_origin() throws Exception {
    mvc.perform(
            options("/api/v1/leads")
                .header("Origin", "http://localhost:8081")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"));
  }

  @Test
  @DisplayName("preflight CORS de origem não permitida é recusado")
  void cors_preflight_from_unknown_origin_is_rejected() throws Exception {
    mvc.perform(
            options("/api/v1/leads")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("endpoints públicos acessíveis sem token (health, OpenAPI, Swagger UI)")
  void public_endpoints_without_token() throws Exception {
    mvc.perform(get("/health")).andExpect(status().isOk());
    mvc.perform(get("/ready")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("ForwardService API"))
        .andExpect(jsonPath("$.info.version").value("3.0.0"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(
            jsonPath("$.servers[*].url")
                .value(Matchers.hasItem("https://forwardservice-api.onrender.com")))
        .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }
}
