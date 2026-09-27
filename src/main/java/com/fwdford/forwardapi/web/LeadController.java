// Lead resource: GET /api/v1/leads (collection), GET /api/v1/leads/{id} and
// PATCH /api/v1/leads/{id}. Validates query/path/body input; RBAC and dealer scoping
// live in LeadService. The collection keeps a JSON array body (mobile compatibility)
// and reports the total in X-Total-Count.
// Recurso lead: listagem, detalhe e PATCH; o total vai no header X-Total-Count.
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.model.Lead;
import com.fwdford.forwardapi.model.LeadFilter;
import com.fwdford.forwardapi.model.PageResult;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.service.LeadService;
import com.fwdford.forwardapi.web.dto.LeadPatchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/leads", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Leads", description = "Leads de retenção (escopo por concessionária).")
public class LeadController {

  private static final List<String> STATUSES =
      List.of("new", "assigned", "contacted", "converted", "lost", "expired");
  private static final List<String> PRIORITIES = List.of("low", "medium", "high", "critical");
  private static final String MERGE_PATCH_JSON = "application/merge-patch+json";

  private final LeadService service;

  public LeadController(LeadService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "listLeads",
      summary = "Listar leads",
      description =
          "Todos os perfis. ATENDENTE e GESTOR recebem apenas leads da própria concessionária"
              + " (dealer_id de outra concessionária resulta em 403); ADMIN vê todas e pode"
              + " filtrar por dealer_id. Ordenado por created_at desc. Corpo: array JSON; total"
              + " no header X-Total-Count.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Página de leads (pode ser vazia)",
        headers =
            @Header(
                name = "X-Total-Count",
                description = "Total de leads que atendem aos filtros",
                schema = @Schema(type = "integer")),
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = Lead.class)))),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "Forbidden"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public ResponseEntity<List<Lead>> list(
      @Parameter(
              description = "Filtra por status.",
              schema =
                  @Schema(
                      allowableValues = {
                        "new",
                        "assigned",
                        "contacted",
                        "converted",
                        "lost",
                        "expired"
                      }))
          @RequestParam(name = "status", required = false)
          String status,
      @Parameter(
              description = "Filtra por prioridade.",
              schema = @Schema(allowableValues = {"low", "medium", "high", "critical"}))
          @RequestParam(name = "priority", required = false)
          String priority,
      @Parameter(
              description = "Filtra por concessionária (ADMIN; demais perfis só a própria).",
              schema = @Schema(format = "uuid"))
          @RequestParam(name = "dealer_id", required = false)
          String dealerId,
      @Parameter(
              description = "Tamanho da página (1 a 200, padrão 50).",
              schema = @Schema(type = "integer", minimum = "1", maximum = "200"))
          @RequestParam(name = "limit", required = false)
          Integer limit,
      @Parameter(
              description = "Deslocamento para paginação (padrão 0).",
              schema = @Schema(type = "integer", minimum = "0"))
          @RequestParam(name = "offset", required = false)
          Integer offset,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
    String validDealer =
        dealerId == null || dealerId.isEmpty()
            ? ""
            : Validations.validateUuid("dealer_id", dealerId);
    LeadFilter filter =
        new LeadFilter(
            validDealer,
            Validations.validateEnum("status", status, STATUSES),
            Validations.validateEnum("priority", priority, PRIORITIES),
            Validations.validateLimit(limit, 50, 200),
            Validations.validateOffset(offset));
    PageResult<Lead> page = service.list(filter, user);
    return ResponseEntity.ok()
        .header(Pagination.TOTAL_COUNT, String.valueOf(page.total()))
        .body(page.items());
  }

  @GetMapping("/{id}")
  @Operation(
      operationId = "getLead",
      summary = "Detalhe do lead",
      description =
          "Inclui dados do cliente, do veículo, da concessionária e do score de churn."
              + " Lead de outra concessionária resulta em 403 ACCESS_OTHER_DEALER.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Lead encontrado",
        content = @Content(schema = @Schema(implementation = Lead.class))),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "Forbidden"),
    @ApiResponse(responseCode = "404", ref = "NotFound"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public Lead get(
      @Parameter(description = "UUID do lead.", example = "a1000000-0000-4000-8000-000000000001")
          @PathVariable
          String id,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
    return service.get(UUID.fromString(Validations.validateUuid("id", id)), user);
  }

  @PatchMapping(
      value = "/{id}",
      consumes = {MediaType.APPLICATION_JSON_VALUE, MERGE_PATCH_JSON})
  @Operation(
      operationId = "patchLead",
      summary = "Atualizar status e/ou notas do lead",
      description =
          "Atualização parcial (idempotente para o mesmo status). Transições válidas: new ->"
              + " assigned|contacted|lost; assigned -> contacted|lost; contacted ->"
              + " converted|lost. Status finais (converted, lost, expired) respondem 409"
              + " LEAD_INVALID_TRANSITION. Status desconhecido responde 400. Toda alteração é"
              + " registrada no audit_log.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Lead atualizado",
        content = @Content(schema = @Schema(implementation = Lead.class))),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "Forbidden"),
    @ApiResponse(responseCode = "404", ref = "NotFound"),
    @ApiResponse(responseCode = "409", ref = "Conflict"),
    @ApiResponse(responseCode = "415", ref = "UnsupportedMediaType"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public Lead patch(
      @Parameter(description = "UUID do lead.", example = "a1000000-0000-4000-8000-000000000001")
          @PathVariable
          String id,
      @Valid @RequestBody LeadPatchRequest body,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
    return service.patch(UUID.fromString(Validations.validateUuid("id", id)), body, user);
  }
}
