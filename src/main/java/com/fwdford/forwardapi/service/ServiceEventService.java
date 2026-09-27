// Service events (service_orders) with full CRUD.
//   GET    list / by id : every profile; ATENDENTE and GESTOR only see their dealer.
//   POST   / PUT        : GESTOR (own dealer only), ADMIN and SERVICE.
//   DELETE              : ADMIN only.
// Body validation beyond Bean Validation: the VIN must exist and the dealer code must be an
// active dealer, otherwise 422 (the request is well-formed but references nothing);
// the same (vin, dealer, type, date) twice is 409 SERVICE_EVENT_DUPLICATE.
// Every write is audited.
// Eventos de servico com CRUD completo, RBAC por perfil e auditoria.
package com.fwdford.forwardapi.service;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.Dealer;
import com.fwdford.forwardapi.model.PageResult;
import com.fwdford.forwardapi.model.ServiceEvent;
import com.fwdford.forwardapi.repository.DealerRepository;
import com.fwdford.forwardapi.repository.ServiceEventRepository;
import com.fwdford.forwardapi.repository.VehicleRepository;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.util.LogSanitizer;
import com.fwdford.forwardapi.web.Validations;
import com.fwdford.forwardapi.web.dto.ServiceEventRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceEventService {

  private static final Logger log = LoggerFactory.getLogger(ServiceEventService.class);
  private static final String DEFAULT_STATUS = "scheduled";

  private final ServiceEventRepository repo;
  private final VehicleRepository vehicles;
  private final DealerRepository dealers;
  private final AuditService audit;

  public ServiceEventService(
      ServiceEventRepository repo,
      VehicleRepository vehicles,
      DealerRepository dealers,
      AuditService audit) {
    this.repo = repo;
    this.vehicles = vehicles;
    this.dealers = dealers;
    this.audit = audit;
  }

  public PageResult<ServiceEvent> list(String vin, int limit, int offset, AuthenticatedUser user) {
    UUID dealerFilter = user.role().dealerScoped() ? UUID.fromString(user.dealerId()) : null;
    return new PageResult<>(
        repo.list(vin, dealerFilter, limit, offset), repo.count(vin, dealerFilter));
  }

  public ServiceEvent get(UUID id, AuthenticatedUser user) {
    ServiceEvent event = load(id);
    if (!user.canAccessDealer(event.dealerId())) {
      throw ApiException.forbiddenOtherDealer();
    }
    return event;
  }

  @PreAuthorize("hasAnyRole('GESTOR', 'ADMIN', 'SERVICE')")
  @Transactional
  public ServiceEvent create(ServiceEventRequest req, AuthenticatedUser user) {
    Resolved r = resolve(req, user);
    if (repo.existsDuplicate(r.vin(), r.dealerId(), r.orderType(), req.serviceDate(), null)) {
      throw duplicate();
    }
    UUID id =
        repo.insert(
            r.vin(),
            r.dealerId(),
            r.orderType(),
            r.status(),
            req.serviceDate(),
            req.km(),
            req.maintenanceNumber(),
            req.mainSource());
    audit.record(user, "service_event.created", "service_event", id.toString(), summary(r));
    log.info(
        "service_event_created id={} vin={} dealer_id={}",
        id,
        LogSanitizer.maskVin(r.vin()),
        r.dealerId());
    return load(id);
  }

  @PreAuthorize("hasAnyRole('GESTOR', 'ADMIN', 'SERVICE')")
  @Transactional
  public ServiceEvent replace(UUID id, ServiceEventRequest req, AuthenticatedUser user) {
    ServiceEvent current = load(id);
    if (!user.canAccessDealer(current.dealerId())) {
      throw ApiException.forbiddenOtherDealer();
    }
    Resolved r = resolve(req, user);
    if (repo.existsDuplicate(r.vin(), r.dealerId(), r.orderType(), req.serviceDate(), id)) {
      throw duplicate();
    }
    repo.update(
        id,
        r.vin(),
        r.dealerId(),
        r.orderType(),
        r.status(),
        req.serviceDate(),
        req.km(),
        req.maintenanceNumber(),
        req.mainSource());
    audit.record(user, "service_event.updated", "service_event", id.toString(), summary(r));
    return load(id);
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public void delete(UUID id, AuthenticatedUser user) {
    ServiceEvent current = load(id);
    repo.deleteById(id);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("vin", current.vin());
    payload.put("dealer_id", current.dealerId());
    payload.put("order_type", current.orderType());
    audit.record(user, "service_event.deleted", "service_event", id.toString(), payload);
    log.info("service_event_deleted id={} actor={}", id, user.id());
  }

  /** Validates references in the body and the caller's right to write for that dealer. */
  private Resolved resolve(ServiceEventRequest req, AuthenticatedUser user) {
    String vin = Validations.validateVin(req.vin());
    String orderType = ServiceEventRepository.SERVICE_CODE_TO_ORDER_TYPE.get(req.serviceCode());
    if (orderType == null) {
      throw ApiException.badRequest("VALIDATION_FAILED", "service_code deve estar entre 1 e 5.");
    }
    if (!vehicles.existsByVin(vin)) {
      throw ApiException.unprocessable(
          "REFERENCED_VEHICLE_NOT_FOUND", "O veículo informado (vin) não existe.");
    }
    Dealer dealer =
        dealers
            .findActiveByCode(req.dealerCode().trim())
            .orElseThrow(
                () ->
                    ApiException.unprocessable(
                        "REFERENCED_DEALER_NOT_FOUND",
                        "A concessionária informada (dealer_code) não existe ou está inativa."));
    if (!user.canAccessDealer(dealer.id())) {
      throw ApiException.forbiddenOtherDealer();
    }
    String status = req.status() == null ? DEFAULT_STATUS : req.status();
    return new Resolved(vin, UUID.fromString(dealer.id()), orderType, status);
  }

  private ServiceEvent load(UUID id) {
    return repo.findById(id)
        .orElseThrow(
            () ->
                ApiException.notFound(
                    "SERVICE_EVENT_NOT_FOUND", "Evento de serviço não encontrado."));
  }

  private static ApiException duplicate() {
    return ApiException.conflict(
        "SERVICE_EVENT_DUPLICATE",
        "Já existe um evento com o mesmo VIN, concessionária, tipo e data.");
  }

  private static Map<String, Object> summary(Resolved r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("vin", r.vin());
    m.put("dealer_id", r.dealerId().toString());
    m.put("order_type", r.orderType());
    m.put("status", r.status());
    return m;
  }

  private record Resolved(String vin, UUID dealerId, String orderType, String status) {}
}
