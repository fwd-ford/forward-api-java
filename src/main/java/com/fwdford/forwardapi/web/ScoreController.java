// GET /api/v1/scores/{customerId}. RBAC is applied inside the service layer.
// GET /api/v1/scores/{customerId}: RBAC aplicado no service.
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.model.ChurnScore;
import com.fwdford.forwardapi.service.ScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scores")
@Tag(name = "Scores", description = "Customer churn score lookup.")
public class ScoreController {

  private final ScoreService service;

  public ScoreController(ScoreService service) {
    this.service = service;
  }

  @GetMapping("/{customerId}")
  @Operation(
      summary = "Get current churn score for a customer",
      description =
          "Returns the most recent churn score computed for the given customer. "
              + "RBAC is enforced inside the service layer.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Score found",
        content = @Content(schema = @Schema(implementation = ChurnScore.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid customer UUID",
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
        description = "No score available for this customer",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public ChurnScore get(
      @Parameter(description = "Customer UUID", required = true) @PathVariable String customerId,
      HttpServletRequest req) {
    String validId = Validations.validateUuid("customerId", customerId);
    AuthPrincipal p = (AuthPrincipal) req.getAttribute(WebAttrs.PRINCIPAL);
    String role = p != null ? p.role() : null;
    return service.getCurrent(validId, role);
  }
}
