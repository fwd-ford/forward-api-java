package com.fwdford.forwardapi.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.annotation.Transactional;

/** Lead collection, detail and PATCH (state machine) over HTTP. Each test rolls back. */
@Transactional
class LeadIT extends IntegrationTest {

  @Test
  @DisplayName("ATENDENTE lista apenas leads da própria concessionária (array + X-Total-Count)")
  void atendente_lists_only_own_dealer() throws Exception {
    mvc.perform(get("/api/v1/leads?limit=200").header("Authorization", bearer(ATENDENTE)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", String.valueOf(TestData.LEADS_DEALER_1)))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(TestData.LEADS_DEALER_1))
        .andExpect(
            jsonPath("$[*].dealer_id").value(Matchers.everyItem(Matchers.is(TestData.DEALER_1))));
  }

  @Test
  @DisplayName("ATENDENTE de outra concessionária enxerga outro conjunto")
  void atendente2_lists_dealer_2() throws Exception {
    mvc.perform(get("/api/v1/leads").header("Authorization", bearer(ATENDENTE_2)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", String.valueOf(TestData.LEADS_DEALER_2)))
        .andExpect(
            jsonPath("$[*].dealer_id").value(Matchers.everyItem(Matchers.is(TestData.DEALER_2))));
  }

  @Test
  @DisplayName("ADMIN lista todas as concessionárias e pode filtrar por dealer_id")
  void admin_lists_everything_and_filters_by_dealer() throws Exception {
    mvc.perform(get("/api/v1/leads?limit=200").header("Authorization", bearer(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", String.valueOf(TestData.LEADS_TOTAL)))
        .andExpect(jsonPath("$.length()").value(TestData.LEADS_TOTAL));
    mvc.perform(
            get("/api/v1/leads?dealer_id=" + TestData.DEALER_2)
                .header("Authorization", bearer(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", String.valueOf(TestData.LEADS_DEALER_2)));
  }

  @Test
  @DisplayName("ATENDENTE pedindo dealer_id de outra concessionária: 403")
  void atendente_filtering_other_dealer_is_403() throws Exception {
    mvc.perform(
            get("/api/v1/leads?dealer_id=" + TestData.DEALER_2)
                .header("Authorization", bearer(ATENDENTE)))
        .andExpect(status().isForbidden())
        .andExpect(problem(403, "ACCESS_OTHER_DEALER"));
  }

  @Test
  @DisplayName("filtros de status e prioridade e paginação limit/offset")
  void filters_and_pagination() throws Exception {
    mvc.perform(get("/api/v1/leads?status=new").header("Authorization", bearer(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", "5"))
        .andExpect(jsonPath("$[*].status").value(Matchers.everyItem(Matchers.is("new"))));
    mvc.perform(get("/api/v1/leads?priority=critical").header("Authorization", bearer(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", "4"));
    mvc.perform(get("/api/v1/leads?limit=2&offset=2").header("Authorization", bearer(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", String.valueOf(TestData.LEADS_TOTAL)))
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  @DisplayName("filtro com status desconhecido ou offset negativo: 400")
  void invalid_filters_are_400() throws Exception {
    mvc.perform(get("/api/v1/leads?status=perdido").header("Authorization", bearer(ADMIN)))
        .andExpect(status().isBadRequest())
        .andExpect(problem(400, "INVALID_PARAMETER"));
    mvc.perform(get("/api/v1/leads?offset=-1").header("Authorization", bearer(ADMIN)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("detalhe do lead traz dados de cliente, veículo e score")
  void get_lead_detail() throws Exception {
    mvc.perform(
            get("/api/v1/leads/" + TestData.LEAD_NEW_DEALER_1)
                .header("Authorization", bearer(ATENDENTE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(TestData.LEAD_NEW_DEALER_1))
        .andExpect(jsonPath("$.status").value("new"))
        .andExpect(jsonPath("$.priority").value("high"))
        .andExpect(jsonPath("$.customer_id").value(TestData.CUSTOMER_DEALER_1))
        .andExpect(jsonPath("$.customer_name").value("João da Silva"))
        .andExpect(jsonPath("$.vehicle_model").value("Ka"))
        .andExpect(jsonPath("$.vehicle_year").value(2018))
        .andExpect(jsonPath("$.churn_probability").value(0.78))
        .andExpect(jsonPath("$.segment").value("esquecido"))
        .andExpect(jsonPath("$.expected_value_brl").value(1200.0))
        .andExpect(jsonPath("$.created_at").isNotEmpty());
  }

  @Test
  @DisplayName("lead inexistente: 404; id inválido: 400")
  void unknown_and_invalid_ids() throws Exception {
    mvc.perform(
            get("/api/v1/leads/" + TestData.UNKNOWN_UUID).header("Authorization", bearer(ADMIN)))
        .andExpect(status().isNotFound())
        .andExpect(problem(404, "LEAD_NOT_FOUND"));
    mvc.perform(get("/api/v1/leads/not-a-uuid").header("Authorization", bearer(ADMIN)))
        .andExpect(status().isBadRequest())
        .andExpect(problem(400, "INVALID_PARAMETER"));
  }

  @Test
  @DisplayName("PATCH new -> contacted: 200, status atualizado e registro no audit_log")
  void patch_valid_transition() throws Exception {
    mvc.perform(
            patch("/api/v1/leads/" + TestData.LEAD_NEW_DEALER_1)
                .header("Authorization", bearer(ATENDENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "contacted", "notes", "Cliente atendeu."))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("contacted"))
        .andExpect(jsonPath("$.notes").value("Cliente atendeu."))
        .andExpect(jsonPath("$.updated_at").isNotEmpty());

    Long audits =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_log WHERE action = 'lead.updated' AND resource_id = :id"
                + " AND payload->>'status_to' = 'contacted' AND actor_role = 'ATENDENTE'",
            new MapSqlParameterSource("id", TestData.LEAD_NEW_DEALER_1),
            Long.class);
    assertThat(audits).isEqualTo(1);
  }

  @Test
  @DisplayName("PATCH contacted -> converted preenche converted_at (merge-patch+json aceito)")
  void patch_to_converted_sets_converted_at() throws Exception {
    mvc.perform(
            patch("/api/v1/leads/" + TestData.LEAD_CONTACTED_DEALER_1)
                .header("Authorization", bearer(GESTOR))
                .contentType("application/merge-patch+json")
                .content(json(Map.of("status", "converted"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("converted"))
        .andExpect(jsonPath("$.converted_at").isNotEmpty());
  }

  @Test
  @DisplayName("PATCH somente de notas mantém o status")
  void patch_notes_only() throws Exception {
    mvc.perform(
            patch("/api/v1/leads/" + TestData.LEAD_NEW_DEALER_1)
                .header("Authorization", bearer(ATENDENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("notes", "Ligar após as 18h."))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("new"))
        .andExpect(jsonPath("$.notes").value("Ligar após as 18h."));
  }

  @Test
  @DisplayName("PATCH a partir de status final (converted -> lost): 409 LEAD_INVALID_TRANSITION")
  void patch_from_terminal_status_is_409() throws Exception {
    mvc.perform(
            patch("/api/v1/leads/" + TestData.LEAD_CONVERTED_DEALER_1)
                .header("Authorization", bearer(ATENDENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "lost"))))
        .andExpect(status().isConflict())
        .andExpect(problem(409, "LEAD_INVALID_TRANSITION"))
        .andExpect(jsonPath("$.detail").value(Matchers.containsString("converted -> lost")));
  }

  @Test
  @DisplayName("PATCH com transição não permitida (new -> converted): 409")
  void patch_skipping_steps_is_409() throws Exception {
    mvc.perform(
            patch("/api/v1/leads/" + TestData.LEAD_NEW_DEALER_1)
                .header("Authorization", bearer(ATENDENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "converted"))))
        .andExpect(status().isConflict())
        .andExpect(problem(409, "LEAD_INVALID_TRANSITION"));
  }

  @Test
  @DisplayName("PATCH com status desconhecido: 400 VALIDATION_FAILED")
  void patch_unknown_status_is_400() throws Exception {
    mvc.perform(
            patch("/api/v1/leads/" + TestData.LEAD_NEW_DEALER_1)
                .header("Authorization", bearer(ATENDENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "ganho"))))
        .andExpect(status().isBadRequest())
        .andExpect(problem(400, "VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("status"));
  }

  @Test
  @DisplayName("PATCH vazio: 400 EMPTY_PATCH")
  void patch_empty_body_is_400() throws Exception {
    mvc.perform(
            patch("/api/v1/leads/" + TestData.LEAD_NEW_DEALER_1)
                .header("Authorization", bearer(ATENDENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(problem(400, "EMPTY_PATCH"));
  }

  @Test
  @DisplayName("PATCH em lead de outra concessionária: 403; lead inexistente: 404")
  void patch_scope_and_not_found() throws Exception {
    mvc.perform(
            patch("/api/v1/leads/" + TestData.LEAD_NEW_DEALER_2)
                .header("Authorization", bearer(ATENDENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "contacted"))))
        .andExpect(status().isForbidden())
        .andExpect(problem(403, "ACCESS_OTHER_DEALER"));
    mvc.perform(
            patch("/api/v1/leads/" + TestData.UNKNOWN_UUID)
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("status", "contacted"))))
        .andExpect(status().isNotFound())
        .andExpect(problem(404, "LEAD_NOT_FOUND"));
  }
}
