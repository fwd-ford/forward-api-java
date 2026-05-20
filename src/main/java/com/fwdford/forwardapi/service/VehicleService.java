// Vehicle service. Sprint 1 scope is read-only and authorization-free once JWT is valid.
// Service de veiculo: escopo Sprint 1 eh somente leitura, sem regras alem do JWT.
package com.fwdford.forwardapi.service;

import org.springframework.stereotype.Service;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.Vehicle;
import com.fwdford.forwardapi.repository.VehicleRepository;

@Service
public class VehicleService {

  private final VehicleRepository repo;

  public VehicleService(VehicleRepository repo) {
    this.repo = repo;
  }

  public Vehicle get(String vin) {
    return repo.findByVin(vin).orElseThrow(() -> ApiException.notFound("vehicle"));
  }
}
