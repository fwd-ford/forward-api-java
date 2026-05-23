// GET /api/v1/vehicles/{vin}. Validates VIN and returns the vehicle.
// GET /api/v1/vehicles/{vin}: valida VIN e retorna o veiculo.
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.model.Vehicle;
import com.fwdford.forwardapi.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles")
@Tag(name = "Vehicles", description = "Vehicle lookup by VIN.")
public class VehicleController {

  private final VehicleService service;

  public VehicleController(VehicleService service) {
    this.service = service;
  }

  @GetMapping("/{vin}")
  @Operation(
      summary = "Get vehicle by VIN",
      description =
          "Returns vehicle data for the given 17-character VIN. "
              + "VIN is validated against ISO 3779 (no I, O, Q).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Vehicle found",
        content = @Content(schema = @Schema(implementation = Vehicle.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid VIN",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Vehicle not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public Vehicle get(
      @Parameter(description = "17-character Vehicle Identification Number", required = true)
          @PathVariable
          String vin) {
    return service.get(Validations.validateVin(vin));
  }
}
