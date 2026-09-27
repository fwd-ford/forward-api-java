// Deprecated alias GET /api/v1/scores/{customerId}. Kept for existing clients; answers
// with Deprecation and Link (successor-version) headers pointing to the canonical
// sub-resource GET /api/v1/customers/{id}/score.
// Alias deprecado; aponta para GET /api/v1/customers/{id}/score via headers.
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.model.ChurnScore;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.service.ScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/scores", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Customers")
public class ScoreController {

  private final ScoreService service;

  public ScoreController(ScoreService service) {
    this.service = service;
  }

  @GetMapping("/{customerId}")
  @Operation(
      operationId = "getCurrentChurnScoreDeprecated",
      summary = "Score de churn (alias deprecado)",
      deprecated = true,
      description =
          "Alias mantido por compatibilidade. Use GET /api/v1/customers/{id}/score. A resposta"
              + " inclui os headers `Deprecation: true` e `Link` com rel=\"successor-version\".")
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
  public ResponseEntity<ChurnScore> get(
      @Parameter(description = "UUID do cliente.", example = "11111111-1111-1111-1111-111111111001")
          @PathVariable
          String customerId,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
    String id = Validations.validateUuid("customerId", customerId);
    ChurnScore score = service.getCurrent(id, user);
    return ResponseEntity.ok()
        .header("Deprecation", "true")
        .header("Link", "</api/v1/customers/" + id + "/score>; rel=\"successor-version\"")
        .body(score);
  }
}
