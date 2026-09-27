package com.fwdford.forwardapi.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Framework errors keep their HTTP status and are rendered as RFC 7807 problems. */
class ErrorHandlingIT extends IntegrationTest {

  @Test
  @DisplayName("método não suportado: 405 METHOD_NOT_ALLOWED com header Allow")
  void method_not_allowed_is_405() throws Exception {
    mvc.perform(delete("/api/v1/leads").header("Authorization", bearer(ADMIN)))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().exists("Allow"))
        .andExpect(problem(405, "METHOD_NOT_ALLOWED"));
  }

  @Test
  @DisplayName("Content-Type não suportado: 415 UNSUPPORTED_MEDIA_TYPE")
  void unsupported_media_type_is_415() throws Exception {
    mvc.perform(
            post("/api/v1/service-events")
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.TEXT_PLAIN)
                .content("vin=9BFZZZ5SZJB000001"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(problem(415, "UNSUPPORTED_MEDIA_TYPE"));
  }

  @Test
  @DisplayName("JSON malformado: 400 MALFORMED_JSON")
  void malformed_json_is_400() throws Exception {
    mvc.perform(
            patch("/api/v1/leads/" + TestData.LEAD_NEW_DEALER_1)
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"contacted\""))
        .andExpect(status().isBadRequest())
        .andExpect(problem(400, "MALFORMED_JSON"));
  }

  @Test
  @DisplayName("tipo incompatível em campo do JSON: 400 INVALID_FIELD_VALUE")
  void wrong_json_field_type_is_400() throws Exception {
    mvc.perform(
            post("/api/v1/service-events")
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vin\":\"9BFZZZ5SZJB000001\",\"service_code\":\"um\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(problem(400, "INVALID_FIELD_VALUE"))
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("service_code")));
  }

  @Test
  @DisplayName("tipo incompatível em query string: 400 TYPE_MISMATCH")
  void query_type_mismatch_is_400() throws Exception {
    mvc.perform(get("/api/v1/leads?limit=abc").header("Authorization", bearer(ADMIN)))
        .andExpect(status().isBadRequest())
        .andExpect(problem(400, "TYPE_MISMATCH"));
  }

  @Test
  @DisplayName("rota inexistente (autenticado): 404 NOT_FOUND em problem+json")
  void unknown_path_is_404() throws Exception {
    mvc.perform(get("/api/v1/nao-existe").header("Authorization", bearer(ADMIN)))
        .andExpect(status().isNotFound())
        .andExpect(problem(404, "NOT_FOUND"));
  }

  @Test
  @DisplayName("X-Request-Id enviado pelo cliente é devolvido no header e no corpo do erro")
  void request_id_is_propagated() throws Exception {
    mvc.perform(
            get("/api/v1/leads/" + TestData.UNKNOWN_UUID)
                .header("Authorization", bearer(ADMIN))
                .header("X-Request-Id", "req-test-0001"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("X-Request-Id", "req-test-0001"))
        .andExpect(jsonPath("$.request_id").value("req-test-0001"));
  }

  @Test
  @DisplayName("X-Request-Id com caracteres inválidos é substituído (evita log injection)")
  void unsafe_request_id_is_replaced() throws Exception {
    mvc.perform(get("/health").header("X-Request-Id", "bad id\nforged-log-line"))
        .andExpect(status().isOk())
        .andExpect(
            header().string("X-Request-Id", Matchers.not(Matchers.containsString("forged"))));
  }

  @Test
  @DisplayName("mensagens de erro em pt-BR com acentuação")
  void messages_are_portuguese_with_accents() throws Exception {
    mvc.perform(
            get("/api/v1/leads/" + TestData.UNKNOWN_UUID).header("Authorization", bearer(ADMIN)))
        .andExpect(jsonPath("$.title").value("Recurso não encontrado"))
        .andExpect(jsonPath("$.detail").value("Lead não encontrado."));
  }
}
