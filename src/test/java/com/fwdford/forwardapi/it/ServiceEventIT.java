package com.fwdford.forwardapi.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Service events: full CRUD and status codes over HTTP. Each test rolls back. */
@Transactional
class ServiceEventIT extends IntegrationTest {

  private static Map<String, Object> body(String vin, String dealerCode, String date) {
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("vin", vin);
    b.put("dealer_code", dealerCode);
    b.put("service_code", 1);
    b.put("maintenance_number", 4);
    b.put("km", 40000);
    b.put("service_date", date);
    b.put("main_source", "dealer_app");
    return b;
  }

  @Test
  @DisplayName("CRUD: POST 201 + Location -> GET 200 -> PUT 200 -> DELETE 204 -> GET 404")
  void full_crud_flow() throws Exception {
    MvcResult created =
        mvc.perform(
                post("/api/v1/service-events")
                    .header("Authorization", bearer(GESTOR))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(body(TestData.VIN_DEALER_1_B, "F0001", "2026-11-10T09:00:00-03:00"))))
            .andExpect(status().isCreated())
            .andExpect(
                header().string("Location", Matchers.containsString("/api/v1/service-events/")))
            .andExpect(jsonPath("$.vin").value(TestData.VIN_DEALER_1_B))
            .andExpect(jsonPath("$.dealer_id").value(TestData.DEALER_1))
            .andExpect(jsonPath("$.dealer_code").value("F0001"))
            .andExpect(jsonPath("$.order_type").value("scheduled_maintenance"))
            .andExpect(jsonPath("$.service_code").value(1))
            .andExpect(jsonPath("$.status").value("scheduled"))
            .andExpect(jsonPath("$.mileage_km").value(40000))
            .andExpect(jsonPath("$.maintenance_number").value(4))
            .andExpect(jsonPath("$.main_source").value("dealer_app"))
            .andReturn();

    JsonNode json = mapper.readTree(created.getResponse().getContentAsString());
    String id = json.get("id").asText();
    String location = created.getResponse().getHeader("Location");
    assertThat(location).endsWith("/api/v1/service-events/" + id);
    String path = URI.create(location).getPath();

    mvc.perform(get(path).header("Authorization", bearer(ATENDENTE)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));

    Map<String, Object> replacement =
        body(TestData.VIN_DEALER_1_B, "F0001", "2026-11-10T09:00:00-03:00");
    replacement.put("status", "completed");
    replacement.put("km", 40150);
    mvc.perform(
            put(path)
                .header("Authorization", bearer(GESTOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(replacement)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("completed"))
        .andExpect(jsonPath("$.mileage_km").value(40150))
        .andExpect(jsonPath("$.completed_at").isNotEmpty());

    mvc.perform(delete(path).header("Authorization", bearer(ADMIN)))
        .andExpect(status().isNoContent())
        .andExpect(jsonPath("$").doesNotExist());

    mvc.perform(get(path).header("Authorization", bearer(ADMIN)))
        .andExpect(status().isNotFound())
        .andExpect(problem(404, "SERVICE_EVENT_NOT_FOUND"));
  }

  @Test
  @DisplayName("POST com VIN inexistente: 422 REFERENCED_VEHICLE_NOT_FOUND")
  void unknown_vehicle_is_422() throws Exception {
    mvc.perform(
            post("/api/v1/service-events")
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body(TestData.VIN_UNKNOWN, "F0001", "2026-11-10T09:00:00-03:00"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(problem(422, "REFERENCED_VEHICLE_NOT_FOUND"));
  }

  @Test
  @DisplayName("POST com concessionária inexistente: 422 REFERENCED_DEALER_NOT_FOUND")
  void unknown_dealer_is_422() throws Exception {
    mvc.perform(
            post("/api/v1/service-events")
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body(TestData.VIN_DEALER_1, "F9999", "2026-11-10T09:00:00-03:00"))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(problem(422, "REFERENCED_DEALER_NOT_FOUND"));
  }

  @Test
  @DisplayName("POST duplicado (mesmo VIN, concessionária, tipo e data): 409")
  void duplicate_is_409() throws Exception {
    String payload = json(body(TestData.VIN_DEALER_1, "F0001", "2026-12-05T08:30:00-03:00"));
    mvc.perform(
            post("/api/v1/service-events")
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/service-events")
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isConflict())
        .andExpect(problem(409, "SERVICE_EVENT_DUPLICATE"));
  }

  @Test
  @DisplayName("POST com campos inválidos: 400 com erros por campo")
  void invalid_body_is_400() throws Exception {
    mvc.perform(
            post("/api/v1/service-events")
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("vin", TestData.VIN_DEALER_1, "service_code", 9))))
        .andExpect(status().isBadRequest())
        .andExpect(problem(400, "VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[?(@.field == 'service_code')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field == 'dealer_code')]").exists());
  }

  @Test
  @DisplayName("corpo legado em camelCase (Sprint 2) continua aceito")
  void legacy_camel_case_body_is_accepted() throws Exception {
    Map<String, Object> legacy = new LinkedHashMap<>();
    legacy.put("vin", TestData.VIN_DEALER_1);
    legacy.put("dealerCode", "F0001");
    legacy.put("serviceCode", 2);
    legacy.put("maintenanceNumber", 0);
    legacy.put("serviceDate", "2026-12-20T10:00:00-03:00");
    legacy.put("mainSource", "n8n");
    mvc.perform(
            post("/api/v1/service-events")
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(legacy)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.order_type").value("recall"))
        .andExpect(jsonPath("$.main_source").value("n8n"));
  }

  @Test
  @DisplayName("GESTOR não registra evento para outra concessionária: 403")
  void gestor_other_dealer_is_403() throws Exception {
    mvc.perform(
            post("/api/v1/service-events")
                .header("Authorization", bearer(GESTOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body(TestData.VIN_DEALER_2, "F0002", "2026-11-10T09:00:00-03:00"))))
        .andExpect(status().isForbidden())
        .andExpect(problem(403, "ACCESS_OTHER_DEALER"));
  }

  @Test
  @DisplayName("listagem com escopo por concessionária e filtro por VIN")
  void list_is_scoped_and_filterable() throws Exception {
    mvc.perform(get("/api/v1/service-events").header("Authorization", bearer(ATENDENTE)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", String.valueOf(TestData.EVENTS_DEALER_1)))
        .andExpect(
            jsonPath("$[*].dealer_id").value(Matchers.everyItem(Matchers.is(TestData.DEALER_1))));
    mvc.perform(
            get("/api/v1/service-events?vin=" + TestData.VIN_DEALER_1)
                .header("Authorization", bearer(ADMIN)))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", "1"))
        .andExpect(jsonPath("$[0].vin").value(TestData.VIN_DEALER_1));
  }

  @Test
  @DisplayName("evento de outra concessionária: 403; PUT em id inexistente: 404")
  void scope_and_not_found() throws Exception {
    mvc.perform(
            get("/api/v1/service-events/" + TestData.EVENT_DEALER_2)
                .header("Authorization", bearer(ATENDENTE)))
        .andExpect(status().isForbidden())
        .andExpect(problem(403, "ACCESS_OTHER_DEALER"));
    mvc.perform(
            put("/api/v1/service-events/" + TestData.UNKNOWN_UUID)
                .header("Authorization", bearer(ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body(TestData.VIN_DEALER_1, "F0001", "2026-11-10T09:00:00-03:00"))))
        .andExpect(status().isNotFound())
        .andExpect(problem(404, "SERVICE_EVENT_NOT_FOUND"));
  }

  @Test
  @DisplayName("integração com X-API-Key (perfil SERVICE) registra evento")
  void service_api_key_can_create() throws Exception {
    mvc.perform(
            post("/api/v1/service-events")
                .header("X-API-Key", props.internalApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body(TestData.VIN_DEALER_2, "F0002", "2026-11-11T09:00:00-03:00"))))
        .andExpect(status().isCreated());
  }
}
