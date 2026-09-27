// Vehicle service. Dealer-scoped: ATENDENTE and GESTOR only read vehicles serviced by
// their dealer (or with a lead owned by it); ADMIN and SERVICE read all. Used by both
// the REST controller and the SOAP GetVehicle operation.
// Service de veiculos com escopo por concessionaria (REST e SOAP).
package com.fwdford.forwardapi.service;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.Vehicle;
import com.fwdford.forwardapi.repository.VehicleRepository;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import org.springframework.stereotype.Service;

@Service
public class VehicleService {

  private final VehicleRepository repo;

  public VehicleService(VehicleRepository repo) {
    this.repo = repo;
  }

  public Vehicle get(String vin, AuthenticatedUser user) {
    Vehicle vehicle =
        repo.findByVin(vin)
            .orElseThrow(
                () -> ApiException.notFound("VEHICLE_NOT_FOUND", "Veículo não encontrado."));
    if (user.role().dealerScoped()
        && !user.canAccessDealer(vehicle.currentDealerId())
        && (user.dealerId() == null || !repo.isLinkedToDealer(vin, user.dealerId()))) {
      throw ApiException.forbiddenOtherDealer();
    }
    return vehicle;
  }
}
