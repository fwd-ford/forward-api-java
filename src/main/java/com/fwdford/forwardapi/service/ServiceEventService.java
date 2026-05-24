// Service event service. Normalises and validates VIN, resolves dealer code,
// maps the integer service code to the order_type enum, then delegates to the
// repository. Throws ApiException for missing VIN/dealer or invalid serviceCode.
// Service de eventos: normaliza VIN, resolve dealer code, mapeia serviceCode
// para o enum order_type e delega ao repositorio. Lanca ApiException para VIN/
// dealer ausentes ou serviceCode invalido.
package com.fwdford.forwardapi.service;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.ServiceEvent;
import com.fwdford.forwardapi.repository.ServiceEventRepository;
import com.fwdford.forwardapi.repository.VehicleRepository;
import com.fwdford.forwardapi.web.CreateServiceEventRequest;
import com.fwdford.forwardapi.web.Validations;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ServiceEventService {

  static final Map<Integer, String> SERVICE_CODE_TO_ORDER_TYPE =
      Map.of(
          1, "scheduled_maintenance",
          2, "recall",
          3, "warranty_repair",
          4, "paid_repair",
          5, "inspection");

  private final ServiceEventRepository repo;
  private final VehicleRepository vehicleRepo;

  public ServiceEventService(ServiceEventRepository repo, VehicleRepository vehicleRepo) {
    this.repo = repo;
    this.vehicleRepo = vehicleRepo;
  }

  public ServiceEvent create(CreateServiceEventRequest req) {
    String vin = Validations.validateVin(req.vin());

    String orderType = SERVICE_CODE_TO_ORDER_TYPE.get(req.serviceCode());
    if (orderType == null) {
      throw ApiException.badRequest("serviceCode deve estar entre 1 e 5.");
    }

    if (vehicleRepo.findByVin(vin).isEmpty()) {
      throw ApiException.notFound("vehicle");
    }

    UUID dealerId =
        repo.findDealerIdByCode(req.dealerCode())
            .orElseThrow(() -> ApiException.notFound("dealer"));

    return repo.insert(
        vin,
        dealerId,
        orderType,
        req.serviceDate(),
        req.km(),
        req.maintenanceNumber(),
        req.mainSource());
  }
}
