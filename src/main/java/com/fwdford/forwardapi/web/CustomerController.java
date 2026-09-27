// Customer resource: GET /api/v1/customers/{id} and its sub-resource
// GET /api/v1/customers/{id}/score (canonical location of the churn score).
// Recurso cliente e sub-recurso score (localizacao canonica do score de churn).
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.model.ChurnScore;
import com.fwdford.forwardapi.model.Customer;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.service.CustomerService;
import com.fwdford.forwardapi.service.ScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/customers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Customers", description = "Clientes e score de churn (escopo por concessionária).")
public class CustomerController {

  private final CustomerService customers;
  private final ScoreService scores;

  public CustomerController(CustomerService customers, ScoreService scores) {
    this.customers = customers;
    this.scores = scores;
  }

  @GetMapping("/{id}")
  @Operation(
      operationId = "getCustomer",
      summary = "Cliente por id",
      description =
          "Todos os perfis. ATENDENTE e GESTOR só enxergam clientes vinculados à própria"
              + " concessionária (veículo atendido ou lead da concessionária); ADMIN vê todos.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Cliente encontrado",
        content = @Content(schema = @Schema(implementation = Customer.class))),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "Forbidden"),
    @ApiResponse(responseCode = "404", ref = "NotFound"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public Customer get(
      @Parameter(description = "UUID do cliente.", example = "11111111-1111-1111-1111-111111111001")
          @PathVariable
          String id,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
    return customers.get(Validations.validateUuid("id", id), user);
  }

  @GetMapping("/{id}/score")
  @Operation(
      operationId = "getCustomerScore",
      summary = "Score de churn atual do cliente",
      description =
          "Último score calculado pelo forward-ml para o cliente. Mesmas regras de escopo de"
              + " GET /api/v1/customers/{id}. 404 SCORE_NOT_FOUND quando não há score.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Score encontrado",
        content = @Content(schema = @Schema(implementation = ChurnScore.class))),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "Forbidden"),
    @ApiResponse(responseCode = "404", ref = "NotFound"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public ChurnScore score(
      @Parameter(description = "UUID do cliente.", example = "11111111-1111-1111-1111-111111111001")
          @PathVariable
          String id,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
    return scores.getCurrent(Validations.validateUuid("id", id), user);
  }
}
