// GET /api/v1/vehicles/{vin}. Validates VIN and returns the vehicle (dealer-scoped).
// GET /api/v1/vehicles/{vin}: valida VIN e retorna o veiculo (escopo por concessionaria).
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.model.Vehicle;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.service.VehicleService;
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
@RequestMapping(value = "/api/v1/vehicles", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Vehicles", description = "Veículos por VIN (escopo por concessionária).")
public class VehicleController {

  private final VehicleService service;

  public VehicleController(VehicleService service) {
    this.service = service;
  }

  @GetMapping("/{vin}")
  @Operation(
      operationId = "getVehicle",
      summary = "Veículo por VIN",
      description =
          "VIN de 17 caracteres (ISO 3779, sem I, O e Q). ATENDENTE e GESTOR só enxergam"
              + " veículos da própria concessionária; ADMIN vê todos.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Veículo encontrado",
        content = @Content(schema = @Schema(implementation = Vehicle.class))),
    @ApiResponse(responseCode = "400", ref = "BadRequest"),
    @ApiResponse(responseCode = "401", ref = "Unauthorized"),
    @ApiResponse(responseCode = "403", ref = "Forbidden"),
    @ApiResponse(responseCode = "404", ref = "NotFound"),
    @ApiResponse(responseCode = "429", ref = "TooManyRequests")
  })
  public Vehicle get(
      @Parameter(
              description = "VIN de 17 caracteres (ISO 3779, sem I/O/Q).",
              schema = @Schema(pattern = "^[A-HJ-NPR-Z0-9]{17}$"),
              example = "9BFZZZ5SZJB000001")
          @PathVariable
          String vin,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
    return service.get(Validations.validateVin(vin), user);
  }
}
