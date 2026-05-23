// GET /api/v1/leads. Applies query-parameter validation before hitting the service.
// GET /api/v1/leads: aplica validacao dos parametros antes de chamar o service.
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.model.Lead;
import com.fwdford.forwardapi.model.LeadFilter;
import com.fwdford.forwardapi.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "Leads", description = "Sales lead listing with optional filters.")
public class LeadController {

  private static final List<String> STATUSES =
      List.of("new", "assigned", "contacted", "converted", "lost", "expired");

  private final LeadService service;

  public LeadController(LeadService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "List leads",
      description =
          "Returns a paginated list of leads optionally filtered by dealer and status. "
              + "Default limit is 50, maximum 200.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "List of leads (may be empty)",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = Lead.class)))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid query parameter (dealer_id, status or limit)",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public List<Lead> list(
      @Parameter(
              description = "Filter by dealer UUID",
              example = "11111111-1111-1111-1111-111111111111")
          @RequestParam(name = "dealer_id", required = false)
          String dealerId,
      @Parameter(
              description = "Filter by status",
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
      @Parameter(description = "Max items to return (default 50, max 200)")
          @RequestParam(name = "limit", required = false)
          String limit) {

    String validDealer = "";
    if (dealerId != null && !dealerId.isEmpty()) {
      validDealer = Validations.validateUuid("dealer_id", dealerId);
    }
    String validStatus = Validations.validateEnum("status", status, STATUSES);
    int validLimit = Validations.validateLimit(limit, 50, 200);

    return service.list(new LeadFilter(validDealer, validStatus, validLimit));
  }
}
