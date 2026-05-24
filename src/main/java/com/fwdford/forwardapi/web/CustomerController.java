// GET /api/v1/customers/{id}. Validates UUID and delegates RBAC to the service.
// GET /api/v1/customers/{id}: valida UUID e delega RBAC ao service.
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.model.Customer;
import com.fwdford.forwardapi.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/customers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Customers", description = "Customer profile lookup with RBAC.")
public class CustomerController {

  private final CustomerService service;

  public CustomerController(CustomerService service) {
    this.service = service;
  }

  @GetMapping("/{id}")
  @Operation(
      operationId = "getCustomer",
      summary = "Get customer by id",
      description =
          "Returns the customer profile for the given UUID. Authentication is required; in"
              + " Sprint 1 any authenticated caller may read any customer (the mobile app needs"
              + " this for the Lead Detail flow — see fwd-ford/forward-api-java#23). Sprint 2"
              + " will tighten access to dealer-scoped reads via the leads table.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Customer found",
        content = @Content(schema = @Schema(implementation = Customer.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid UUID",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden by RBAC",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Customer not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "429",
        description = "Rate limit exceeded",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public Customer get(
      @Parameter(
              description = "Customer UUID.",
              required = true,
              schema = @Schema(format = "uuid"),
              example = "2ddd2b47-9a80-4a0c-8c0a-8ee35d6f8b10")
          @PathVariable
          String id,
      HttpServletRequest req) {
    String validId = Validations.validateUuid("id", id);
    AuthPrincipal p = (AuthPrincipal) req.getAttribute(WebAttrs.PRINCIPAL);
    String sub = p != null ? p.sub() : null;
    String role = p != null ? p.role() : null;
    return service.get(validId, sub, role);
  }
}
