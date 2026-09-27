// Service event resource, full CRUD:
//   GET /api/v1/service-events, GET /{id}, POST (201 + Location), PUT /{id}, DELETE /{id} (204).
// Controller only parses and validates input; RBAC and business rules live in the service.
// Recurso evento de servico com CRUD completo e status HTTP coerentes.
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.model.PageResult;
import com.fwdford.forwardapi.model.ServiceEvent;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.service.ServiceEventService;
import com.fwdford.forwardapi.web.dto.ServiceEventRequest;
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
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping(value = "/api/v1/service-events", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
    name = "Service Events",
    description =
        "Eventos de serviço (revisões, recalls, reparos). Leitura: todos os perfis; escrita:"
            + " GESTOR e ADMIN; exclusão: ADMIN.")
public class ServiceEventController {

  private final ServiceEventService service;

  public ServiceEventController(ServiceEventService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      operationId = "listServiceEvents",
      summary = "Listar eventos de serviço",
      description =
          "Todos os perfis. ATENDENTE e GESTOR veem apenas eventos da própria concessionária."
              + " Total no header X-Total-Count.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Página de eventos",
        headers =
            @Header(
                name = "X-Total-Count",
                description = "Total de eventos que atendem aos filtros",
                schema = @Schema(type = "integer")),
        content =
            @Content(array = @ArraySchema(schema = @Schema(implementation = ServiceEvent.class)))),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public ResponseEntity<List<ServiceEvent>> list(
      @Parameter(description = "Filtra por VIN.", example = "9BFZZZ5SZJB000001")
          @RequestParam(name = "vin", required = false)
          String vin,
      @Parameter(
              description = "Tamanho da página (1 a 200, padrão 50).",
              schema = @Schema(type = "integer", minimum = "1", maximum = "200"))
          @RequestParam(name = "limit", required = false)
          Integer limit,
      @Parameter(
              description = "Deslocamento (padrão 0).",
              schema = @Schema(type = "integer", minimum = "0"))
          @RequestParam(name = "offset", required = false)
          Integer offset,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
    String validVin = vin == null || vin.isBlank() ? null : Validations.validateVin(vin);
    PageResult<ServiceEvent> page =
        service.list(
            validVin,
            Validations.validateLimit(limit, 50, 200),
            Validations.validateOffset(offset),
            user);
    return ResponseEntity.ok()
        .header(Pagination.TOTAL_COUNT, String.valueOf(page.total()))
        .body(page.items());
  }

  @GetMapping("/{id}")
  @Operation(operationId = "getServiceEvent", summary = "Evento de serviço por id")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Evento encontrado",
        content = @Content(schema = @Schema(implementation = ServiceEvent.class))),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "Forbidden"),
    @ApiResponse(responseCode = "404", ref = "NotFound"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public ServiceEvent get(
      @Parameter(description = "UUID do evento.") @PathVariable String id,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
    return service.get(UUID.fromString(Validations.validateUuid("id", id)), user);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "createServiceEvent",
      summary = "Registrar evento de serviço",
      description =
          "GESTOR (somente a própria concessionária), ADMIN ou integração (X-API-Key). Responde"
              + " 201 com o recurso e o header Location. VIN ou concessionária inexistentes: 422."
              + " Evento duplicado (mesmo VIN, concessionária, tipo e data): 409.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Evento criado",
        headers = @Header(name = "Location", description = "URL do novo recurso"),
        content = @Content(schema = @Schema(implementation = ServiceEvent.class))),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "Forbidden"),
    @ApiResponse(responseCode = "409", ref = "Conflict"),
    @ApiResponse(responseCode = "415", ref = "UnsupportedMediaType"),
    @ApiResponse(responseCode = "422", ref = "UnprocessableEntity"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public ResponseEntity<ServiceEvent> create(
      @Valid @RequestBody ServiceEventRequest req,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
    ServiceEvent created = service.create(req, user);
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      operationId = "replaceServiceEvent",
      summary = "Substituir evento de serviço",
      description =
          "PUT com a representação completa (mesmo corpo do POST, status opcional). Mesmas"
              + " regras de perfil e validação do POST; id inexistente responde 404.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Evento atualizado",
        content = @Content(schema = @Schema(implementation = ServiceEvent.class))),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "Forbidden"),
    @ApiResponse(responseCode = "404", ref = "NotFound"),
    @ApiResponse(responseCode = "409", ref = "Conflict"),
    @ApiResponse(responseCode = "415", ref = "UnsupportedMediaType"),
    @ApiResponse(responseCode = "422", ref = "UnprocessableEntity"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public ServiceEvent replace(
      @Parameter(description = "UUID do evento.") @PathVariable String id,
      @Valid @RequestBody ServiceEventRequest req,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
    return service.replace(UUID.fromString(Validations.validateUuid("id", id)), req, user);
  }

  @DeleteMapping("/{id}")
  @Operation(
      operationId = "deleteServiceEvent",
      summary = "Excluir evento de serviço",
      description = "Somente ADMIN. Responde 204 sem corpo.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Evento excluído"),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "Forbidden"),
    @ApiResponse(responseCode = "404", ref = "NotFound"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public ResponseEntity<Void> delete(
      @Parameter(description = "UUID do evento.") @PathVariable String id,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
    service.delete(UUID.fromString(Validations.validateUuid("id", id)), user);
    return ResponseEntity.noContent().build();
  }
}
