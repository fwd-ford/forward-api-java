// GET /api/v1/vehicles/{vin}. Validates VIN and returns the vehicle.
// GET /api/v1/vehicles/{vin}: valida VIN e retorna o veiculo.
package com.fwdford.forwardapi.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fwdford.forwardapi.model.Vehicle;
import com.fwdford.forwardapi.service.VehicleService;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleService service;

    public VehicleController(VehicleService service) {
        this.service = service;
    }

    @GetMapping("/{vin}")
    public Vehicle get(@PathVariable String vin) {
        return service.get(Validations.validateVin(vin));
    }
}
