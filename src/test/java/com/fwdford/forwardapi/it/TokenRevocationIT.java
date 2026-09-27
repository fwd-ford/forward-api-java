package com.fwdford.forwardapi.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session revocation: a token issued before an admin changes (or deletes) the user stops working on
 * the next request, well before it expires. Each test rolls back.
 */
@Transactional
class TokenRevocationIT extends IntegrationTest {

  /** Logs in through the real endpoint and returns the "Bearer ..." header value. */
  private String login(String email, String password) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(uniqueIp())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + mapper.readTree(body).get("access_token").asText();
  }

  private void adminPatch(String userId, Map<String, Object> body) throws Exception {
    mvc.perform(
            patch("/api/v1/users/" + userId)
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("usuário desativado: o token antigo deixa de valer na hora (401 AUTH_TOKEN_REVOKED)")
  void deactivation_revokes_existing_token() throws Exception {
    String token = login("atendente@forward.dev", TestData.PASSWORD);
    mvc.perform(get("/api/v1/leads").header("Authorization", token)).andExpect(status().isOk());

    adminPatch(TestData.ATENDENTE_ID, Map.of("active", false));

    mvc.perform(get("/api/v1/leads").header("Authorization", token))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", Matchers.containsString("invalid_token")))
        .andExpect(problem(401, "AUTH_TOKEN_REVOKED"))
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("Sessão revogada")));
  }

  @Test
  @DisplayName("ADMIN rebaixado a GESTOR perde /users: token antigo 401, novo login 403")
  void demotion_revokes_existing_admin_token() throws Exception {
    Map<String, Object> newAdmin = new LinkedHashMap<>();
    newAdmin.put("email", "segundo.admin@forward.dev");
    newAdmin.put("name", "Segundo Admin");
    newAdmin.put("password", "Admin@2026x");
    newAdmin.put("role", "ADMIN");
    String created =
        mvc.perform(
                post("/api/v1/users")
                    .header("Authorization", bearer(ADMIN))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(newAdmin)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = mapper.readTree(created).get("id").asText();
    String adminToken = login("segundo.admin@forward.dev", "Admin@2026x");
    mvc.perform(get("/api/v1/users").header("Authorization", adminToken))
        .andExpect(status().isOk());

    adminPatch(id, Map.of("role", "GESTOR", "dealer_id", TestData.DEALER_1));

    mvc.perform(get("/api/v1/users").header("Authorization", adminToken))
        .andExpect(status().isUnauthorized())
        .andExpect(problem(401, "AUTH_TOKEN_REVOKED"));
    String gestorToken = login("segundo.admin@forward.dev", "Admin@2026x");
    mvc.perform(get("/api/v1/users").header("Authorization", gestorToken))
        .andExpect(status().isForbidden())
        .andExpect(problem(403, "ACCESS_DENIED"));
  }

  @Test
  @DisplayName("usuário movido para outra concessionária: token antigo 401, novo token vê a nova")
  void dealer_move_revokes_existing_token() throws Exception {
    String token = login("atendente@forward.dev", TestData.PASSWORD);
    mvc.perform(get("/api/v1/leads").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", String.valueOf(TestData.LEADS_DEALER_1)));

    adminPatch(TestData.ATENDENTE_ID, Map.of("dealer_id", TestData.DEALER_2));

    mvc.perform(get("/api/v1/leads").header("Authorization", token))
        .andExpect(status().isUnauthorized())
        .andExpect(problem(401, "AUTH_TOKEN_REVOKED"));
    String fresh = login("atendente@forward.dev", TestData.PASSWORD);
    mvc.perform(get("/api/v1/leads").header("Authorization", fresh))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", String.valueOf(TestData.LEADS_DEALER_2)))
        .andExpect(
            jsonPath("$[*].dealer_id").value(Matchers.everyItem(Matchers.is(TestData.DEALER_2))));
  }

  @Test
  @DisplayName("usuário excluído: token antigo 401 AUTH_TOKEN_REVOKED")
  void deletion_revokes_existing_token() throws Exception {
    String token = login("atendente2@forward.dev", TestData.PASSWORD);
    mvc.perform(get("/api/v1/me").header("Authorization", token)).andExpect(status().isOk());

    mvc.perform(
            delete("/api/v1/users/" + TestData.ATENDENTE_2_ID)
                .header("Authorization", bearer(ADMIN)))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/me").header("Authorization", token))
        .andExpect(status().isUnauthorized())
        .andExpect(problem(401, "AUTH_TOKEN_REVOKED"));
  }

  @Test
  @DisplayName("PATCH active=true num usuário ativo força novo login (token_version incrementado)")
  void same_state_security_edit_still_revokes() throws Exception {
    String token = login("gestor@forward.dev", TestData.PASSWORD);

    adminPatch(TestData.GESTOR_ID, Map.of("active", true));

    mvc.perform(get("/api/v1/me").header("Authorization", token))
        .andExpect(status().isUnauthorized())
        .andExpect(problem(401, "AUTH_TOKEN_REVOKED"));
    String fresh = login("gestor@forward.dev", TestData.PASSWORD);
    String payload = fresh.substring("Bearer ".length()).split("\\.")[1];
    JsonNode claims = mapper.readTree(java.util.Base64.getUrlDecoder().decode(payload.getBytes()));
    org.assertj.core.api.Assertions.assertThat(claims.get("token_version").asLong()).isEqualTo(1);
    mvc.perform(get("/api/v1/me").header("Authorization", fresh)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("redefinição de senha pelo ADMIN revoga os tokens do usuário")
  void password_reset_revokes_existing_token() throws Exception {
    String token = login("atendente@forward.dev", TestData.PASSWORD);

    adminPatch(TestData.ATENDENTE_ID, Map.of("password", "NovaSenha@2026"));

    mvc.perform(get("/api/v1/me").header("Authorization", token))
        .andExpect(status().isUnauthorized())
        .andExpect(problem(401, "AUTH_TOKEN_REVOKED"));
    login("atendente@forward.dev", "NovaSenha@2026");
  }

  @Test
  @DisplayName("alterar só o nome não revoga o token")
  void name_only_edit_keeps_token_valid() throws Exception {
    String token = login("atendente@forward.dev", TestData.PASSWORD);

    adminPatch(TestData.ATENDENTE_ID, Map.of("name", "Beatriz Santos Lima"));

    mvc.perform(get("/api/v1/me").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Beatriz Santos Lima"));
  }

  @Test
  @DisplayName("chamadas com X-API-Key (perfil SERVICE) não dependem de app_users")
  void service_api_key_is_unaffected() throws Exception {
    adminPatch(TestData.ATENDENTE_ID, Map.of("active", false));
    mvc.perform(get("/api/v1/me").header("X-API-Key", props.internalApiKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("SERVICE"));
  }
}
