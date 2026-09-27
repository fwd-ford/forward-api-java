package com.fwdford.forwardapi.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** POST /api/v1/auth/login and GET /api/v1/me over HTTP, against the seeded database. */
@Transactional
class AuthIT extends IntegrationTest {

  private MvcResult login(String email, String password) throws Exception {
    return mvc.perform(
            post("/api/v1/auth/login")
                .with(uniqueIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))))
        .andReturn();
  }

  @Test
  @DisplayName("login válido devolve JWT Bearer e dados do usuário; o token funciona em /me")
  void login_success_returns_token_that_works_on_me() throws Exception {
    MvcResult result = login("gestor@forward.dev", TestData.PASSWORD);
    assertThat(result.getResponse().getStatus()).isEqualTo(200);

    JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
    assertThat(body.get("token_type").asText()).isEqualTo("Bearer");
    assertThat(body.get("expires_in").asLong()).isEqualTo(3600);
    assertThat(body.get("expires_at").asText()).isNotBlank();
    assertThat(body.get("access_token").asText().split("\\.")).hasSize(3);
    JsonNode user = body.get("user");
    assertThat(user.get("id").asText()).isEqualTo(TestData.GESTOR_ID);
    assertThat(user.get("email").asText()).isEqualTo("gestor@forward.dev");
    assertThat(user.get("name").asText()).isEqualTo("Gustavo Mendes");
    assertThat(user.get("role").asText()).isEqualTo("GESTOR");
    assertThat(user.get("dealer_id").asText()).isEqualTo(TestData.DEALER_1);
    assertThat(user.get("dealer_name").asText()).isEqualTo("Ford Morumbi São Paulo");
    assertThat(user.has("password_hash")).isFalse();

    String token = body.get("access_token").asText();
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(TestData.GESTOR_ID))
        .andExpect(jsonPath("$.role").value("GESTOR"))
        .andExpect(jsonPath("$.dealer_id").value(TestData.DEALER_1))
        .andExpect(jsonPath("$.permissions[?(@ == 'service-events:write')]").exists());
  }

  @Test
  @DisplayName("e-mail é comparado sem diferenciar maiúsculas")
  void login_email_is_case_insensitive() throws Exception {
    assertThat(login("ATENDENTE@Forward.DEV", TestData.PASSWORD).getResponse().getStatus())
        .isEqualTo(200);
  }

  @Test
  @DisplayName("senha errada: 401 AUTH_INVALID_CREDENTIALS")
  void wrong_password_is_401() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .with(uniqueIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "gestor@forward.dev", "password", "Errada@2026"))))
        .andExpect(status().isUnauthorized())
        .andExpect(problem(401, "AUTH_INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
  }

  @Test
  @DisplayName("e-mail inexistente: mesma resposta 401 da senha errada (não revela contas)")
  void unknown_email_gets_same_401_as_wrong_password() throws Exception {
    JsonNode unknown =
        mapper.readTree(
            login("ninguem@forward.dev", TestData.PASSWORD).getResponse().getContentAsString());
    JsonNode wrong =
        mapper.readTree(
            login("gestor@forward.dev", "Errada@2026").getResponse().getContentAsString());

    assertThat(unknown.get("status").asInt()).isEqualTo(401);
    assertThat(unknown.get("code").asText()).isEqualTo(wrong.get("code").asText());
    assertThat(unknown.get("title").asText()).isEqualTo(wrong.get("title").asText());
    assertThat(unknown.get("detail").asText()).isEqualTo(wrong.get("detail").asText());
  }

  @Test
  @DisplayName("usuário inativo com senha correta: 401 AUTH_USER_DISABLED")
  void inactive_user_is_401_disabled() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .with(uniqueIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(Map.of("email", "inativo@forward.dev", "password", TestData.PASSWORD))))
        .andExpect(status().isUnauthorized())
        .andExpect(problem(401, "AUTH_USER_DISABLED"));
  }

  @Test
  @DisplayName("corpo sem campos: 400 VALIDATION_FAILED com erros por campo")
  void missing_fields_is_400() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .with(uniqueIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(problem(400, "VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());
  }

  @Test
  @DisplayName("e-mail em formato inválido: 400")
  void invalid_email_format_is_400() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .with(uniqueIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "nao-e-email", "password", "x"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("email"));
  }

  @Test
  @DisplayName("JSON malformado no login: 400 MALFORMED_JSON")
  void malformed_json_is_400() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .with(uniqueIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": "))
        .andExpect(status().isBadRequest())
        .andExpect(problem(400, "MALFORMED_JSON"));
  }

  @Test
  @DisplayName("limite de 5 tentativas de login por minuto por IP: 6a tentativa recebe 429")
  void login_is_rate_limited_per_ip() throws Exception {
    String ip = "10.99.99.99";
    for (int i = 0; i < props.rateLimit().loginMax(); i++) {
      mvc.perform(
              post("/api/v1/auth/login")
                  .with(fromIp(ip))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(Map.of("email", "gestor@forward.dev", "password", "Errada@2026"))))
          .andExpect(status().isUnauthorized());
    }
    mvc.perform(
            post("/api/v1/auth/login")
                .with(fromIp(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(Map.of("email", "gestor@forward.dev", "password", TestData.PASSWORD))))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(problem(429, "RATE_LIMITED"));
  }

  @Test
  @DisplayName("login bem-sucedido e falho são gravados no audit_log")
  void login_attempts_are_audited() throws Exception {
    login("atendente2@forward.dev", TestData.PASSWORD);
    login("atendente2@forward.dev", "Errada@2026");
    Long ok =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE action = 'auth.login_succeeded'"
                + " AND resource_id = :id",
            new MapSqlParameterSource("id", TestData.ATENDENTE_2_ID),
            Long.class);
    Long failed =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE action = 'auth.login_failed'"
                + " AND resource_id = :id",
            new MapSqlParameterSource("id", TestData.ATENDENTE_2_ID),
            Long.class);
    assertThat(ok).isGreaterThanOrEqualTo(1);
    assertThat(failed).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("GET /me do ADMIN: sem concessionária e com permissão users:manage")
  void me_for_admin() throws Exception {
    mvc.perform(get("/api/v1/me").header("Authorization", bearer(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.dealer_id").value(Matchers.nullValue()))
        .andExpect(jsonPath("$.permissions[?(@ == 'users:manage')]").exists());
  }

  @Test
  @DisplayName("GET /me com X-API-Key interna: perfil SERVICE")
  void me_with_internal_api_key() throws Exception {
    mvc.perform(get("/api/v1/me").header("X-API-Key", props.internalApiKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("SERVICE"));
  }
}
