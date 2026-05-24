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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ServiceEventService {

  private static final Logger log = LoggerFactory.getLogger(ServiceEventService.class);

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
      throw new IllegalStateException(
          "serviceCode fora do range 1..5 deveria ter sido barrado pelo Bean Validation: "
              + req.serviceCode());
    }

    if (vehicleRepo.findByVin(vin).isEmpty()) {
      log.warn("service_event_rejected reason=vin_not_found vin={}", vin);
      throw ApiException.notFound("vehicle");
    }

    UUID dealerId =
        repo.findDealerIdByCode(req.dealerCode())
            .orElseThrow(
                () -> {
                  log.warn(
                      "service_event_rejected reason=dealer_not_found dealer_code={}",
                      req.dealerCode());
                  return ApiException.notFound("dealer");
                });

    ServiceEvent created =
        repo.insert(
            vin,
            dealerId,
            orderType,
            req.serviceDate(),
            req.km(),
            req.maintenanceNumber(),
            req.mainSource());
    log.info(
        "service_event_created id={} vin={} dealer_id={}", created.id(), vin, dealerId);
    return created;
  }
}
