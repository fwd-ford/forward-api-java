package com.fwdford.forwardapi.it;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** User administration (ADMIN only) over HTTP. Each test rolls back. */
@Transactional
class UserIT extends IntegrationTest {

  private static Map<String, Object> newUser(String email, String password, String role) {
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("email", email);
    b.put("name", "Marina Lopes");
    b.put("password", password);
    b.put("role", role);
    b.put("dealer_id", TestData.DEALER_2);
    return b;
  }

  private MvcResult create(Map<String, Object> body) throws Exception {
    return mvc.perform(
            post("/api/v1/users")
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
        .andReturn();
  }

  @Test
  @DisplayName("ADMIN lista usuários com X-Total-Count e sem hash de senha")
  void admin_lists_users() throws Exception {
    mvc.perform(get("/api/v1/users").header("Authorization", bearer(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", "6"))
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[0].password_hash").doesNotExist())
        .andExpect(jsonPath("$[0].password").doesNotExist());
    mvc.perform(
            get("/api/v1/users?role=ATENDENTE&active=true").header("Authorization", bearer(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", "2"));
  }

  @Test
  @DisplayName("POST cria usuário (201 + Location), senha gravada como BCrypt e login funciona")
  void create_user_then_login() throws Exception {
    MvcResult result = create(newUser("marina.lopes@forward.dev", "Troca@2026", "ATENDENTE"));
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
    String id = body.get("id").asText();
    assertThat(result.getResponse().getHeader("Location")).endsWith("/api/v1/users/" + id);
    assertThat(body.get("email").asText()).isEqualTo("marina.lopes@forward.dev");
    assertThat(body.get("role").asText()).isEqualTo("ATENDENTE");
    assertThat(body.get("dealer_id").asText()).isEqualTo(TestData.DEALER_2);
    assertThat(body.get("dealer_name").asText()).isEqualTo("Ford Barra Rio");
    assertThat(body.get("active").asBoolean()).isTrue();
    assertThat(body.has("password")).isFalse();

    String hash =
        jdbc.queryForObject(
            "SELECT password_hash FROM app_users WHERE id = CAST(:id AS uuid)",
            new MapSqlParameterSource("id", id),
            String.class);
    assertThat(hash).startsWith("$2a$10$").doesNotContain("Troca@2026");

    mvc.perform(
            post("/api/v1/auth/login")
                .with(uniqueIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(Map.of("email", "marina.lopes@forward.dev", "password", "Troca@2026"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.role").value("ATENDENTE"));
  }

  @Test
  @DisplayName("e-mail duplicado (sem diferenciar maiúsculas): 409 USER_EMAIL_TAKEN")
  void duplicate_email_is_409() throws Exception {
    MvcResult result = create(newUser("ADMIN@Forward.dev", "Troca@2026", "GESTOR"));
    assertThat(result.getResponse().getStatus()).isEqualTo(409);
    assertThat(mapper.readTree(result.getResponse().getContentAsString()).get("code").asText())
        .isEqualTo("USER_EMAIL_TAKEN");
  }

  @Test
  @DisplayName("senha fraca: 400 VALIDATION_FAILED no campo password")
  void weak_password_is_400() throws Exception {
    mvc.perform(
            post("/api/v1/users")
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(newUser("fraca@forward.dev", "12345678", "ATENDENTE"))))
        .andExpect(status().isBadRequest())
        .andExpect(problem(400, "VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());
  }

  @Test
  @DisplayName("ATENDENTE sem dealer_id: 400; concessionária inexistente: 422")
  void dealer_rules() throws Exception {
    Map<String, Object> noDealer = newUser("sem.dealer@forward.dev", "Troca@2026", "ATENDENTE");
    noDealer.remove("dealer_id");
    mvc.perform(
            post("/api/v1/users")
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(noDealer)))
        .andExpect(status().isBadRequest())
        .andExpect(problem(400, "USER_DEALER_REQUIRED"));

    Map<String, Object> ghostDealer = newUser("fantasma@forward.dev", "Troca@2026", "GESTOR");
    ghostDealer.put("dealer_id", TestData.UNKNOWN_UUID);
    mvc.perform(
            post("/api/v1/users")
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(ghostDealer)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(problem(422, "REFERENCED_DEALER_NOT_FOUND"));
  }

  @Test
  @DisplayName("PATCH desativa usuário; login passa a responder 401 AUTH_USER_DISABLED")
  void deactivate_user_blocks_login() throws Exception {
    mvc.perform(
            patch("/api/v1/users/" + TestData.ATENDENTE_2_ID)
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("active", false, "name", "Diego C."))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.name").value("Diego C."));

    mvc.perform(
            post("/api/v1/auth/login")
                .with(uniqueIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(Map.of("email", "atendente2@forward.dev", "password", TestData.PASSWORD))))
        .andExpect(status().isUnauthorized())
        .andExpect(problem(401, "AUTH_USER_DISABLED"));
  }

  @Test
  @DisplayName("PATCH promove GESTOR a ADMIN e remove a concessionária")
  void promote_to_admin_clears_dealer() throws Exception {
    mvc.perform(
            patch("/api/v1/users/" + TestData.GESTOR_ID)
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("role", "ADMIN"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.dealer_id").value(Matchers.nullValue()));
  }

  @Test
  @DisplayName("ADMIN não altera o próprio perfil nem se desativa: 409")
  void self_modification_is_409() throws Exception {
    mvc.perform(
            patch("/api/v1/users/" + TestData.ADMIN_ID)
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("role", "GESTOR", "dealer_id", TestData.DEALER_1))))
        .andExpect(status().isConflict())
        .andExpect(problem(409, "USER_SELF_MODIFICATION"));
  }

  @Test
  @DisplayName("DELETE 204 e depois GET 404")
  void delete_user() throws Exception {
    mvc.perform(
            delete("/api/v1/users/" + TestData.INACTIVE_ID).header("Authorization", bearer(ADMIN)))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/users/" + TestData.INACTIVE_ID).header("Authorization", bearer(ADMIN)))
        .andExpect(status().isNotFound())
        .andExpect(problem(404, "USER_NOT_FOUND"));
  }

  @Test
  @DisplayName("ADMIN excluindo a si mesmo: 409 USER_SELF_DELETE")
  void self_delete_is_409() throws Exception {
    mvc.perform(delete("/api/v1/users/" + TestData.ADMIN_ID).header("Authorization", bearer(ADMIN)))
        .andExpect(status().isConflict())
        .andExpect(problem(409, "USER_SELF_DELETE"));
  }

  @Test
  @DisplayName("usuário inexistente: 404")
  void unknown_user_is_404() throws Exception {
    mvc.perform(
            get("/api/v1/users/" + TestData.UNKNOWN_UUID).header("Authorization", bearer(ADMIN)))
        .andExpect(status().isNotFound())
        .andExpect(problem(404, "USER_NOT_FOUND"));
    mvc.perform(
            delete("/api/v1/users/" + TestData.UNKNOWN_UUID).header("Authorization", bearer(ADMIN)))
        .andExpect(status().isNotFound());
  }
}
