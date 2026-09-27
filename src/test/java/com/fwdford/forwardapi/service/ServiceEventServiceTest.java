package com.fwdford.forwardapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.Dealer;
import com.fwdford.forwardapi.model.ServiceEvent;
import com.fwdford.forwardapi.repository.DealerRepository;
import com.fwdford.forwardapi.repository.ServiceEventRepository;
import com.fwdford.forwardapi.repository.VehicleRepository;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.security.Role;
import com.fwdford.forwardapi.web.dto.ServiceEventRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Business rules of service events (repositories mocked). */
class ServiceEventServiceTest {

  private static final String VIN = "9BFZZZ5SZJB000001";
  private static final String DEALER_1 = "d0000000-0000-4000-8000-000000000001";
  private static final String DEALER_2 = "d0000000-0000-4000-8000-000000000002";
  private static final OffsetDateTime SERVICE_DATE = OffsetDateTime.parse("2026-10-20T10:00:00Z");
  private static final UUID EVENT_ID = UUID.fromString("5e000000-0000-4000-8000-0000000000aa");

  private ServiceEventRepository repo;
  private VehicleRepository vehicles;
  private DealerRepository dealers;
  private AuditService audit;
  private ServiceEventService service;

  @BeforeEach
  void setup() {
    repo = Mockito.mock(ServiceEventRepository.class);
    vehicles = Mockito.mock(VehicleRepository.class);
    dealers = Mockito.mock(DealerRepository.class);
    audit = Mockito.mock(AuditService.class);
    service = new ServiceEventService(repo, vehicles, dealers, audit);
    when(vehicles.existsByVin(VIN)).thenReturn(true);
    when(dealers.findActiveByCode("F0001"))
        .thenReturn(Optional.of(new Dealer(DEALER_1, "F0001", "Ford Morumbi", true)));
  }

  private static AuthenticatedUser user(Role role, String dealerId) {
    return new AuthenticatedUser(
        "ad000000-0000-4000-8000-000000000099", "u@forward.dev", "U", role, dealerId);
  }

  private static ServiceEventRequest request(String vin, String dealerCode, int serviceCode) {
    return new ServiceEventRequest(
        vin, dealerCode, serviceCode, 3, 50000, SERVICE_DATE, "dealer_app", null);
  }

  private static ServiceEvent event(String dealerId) {
    return new ServiceEvent(
        EVENT_ID.toString(),
        VIN,
        dealerId,
        "F0001",
        "scheduled_maintenance",
        1,
        "scheduled",
        SERVICE_DATE,
        null,
        50000,
        3,
        "dealer_app",
        SERVICE_DATE,
        SERVICE_DATE);
  }

  @Test
  void gestor_creates_event_for_own_dealer_and_it_is_audited() {
    when(repo.insert(
            eq(VIN),
            eq(UUID.fromString(DEALER_1)),
            eq("scheduled_maintenance"),
            eq("scheduled"),
            eq(SERVICE_DATE),
            eq(50000),
            eq(3),
            eq("dealer_app")))
        .thenReturn(EVENT_ID);
    when(repo.findById(EVENT_ID)).thenReturn(Optional.of(event(DEALER_1)));

    ServiceEvent created = service.create(request(VIN, "F0001", 1), user(Role.GESTOR, DEALER_1));

    assertEquals(EVENT_ID.toString(), created.id());
    verify(audit)
        .record(any(), eq("service_event.created"), eq("service_event"), anyString(), anyMap());
  }

  @Test
  void unknown_vin_yields_422_and_does_not_insert() {
    when(vehicles.existsByVin(anyString())).thenReturn(false);
    ApiException ex =
        assertThrows(
            ApiException.class,
            () -> service.create(request(VIN, "F0001", 1), user(Role.ADMIN, null)));
    assertEquals("REFERENCED_VEHICLE_NOT_FOUND", ex.code());
    assertEquals(422, ex.status().value());
    verify(repo, never())
        .insert(anyString(), any(), anyString(), anyString(), any(), any(), anyInt(), anyString());
  }

  @Test
  void malformed_vin_yields_400_and_does_not_touch_repositories() {
    ApiException ex =
        assertThrows(
            ApiException.class,
            () -> service.create(request("SHORT", "F0001", 1), user(Role.ADMIN, null)));
    assertEquals("INVALID_PARAMETER", ex.code());
    verify(vehicles, never()).existsByVin(anyString());
    verify(dealers, never()).findActiveByCode(anyString());
  }

  @Test
  void out_of_range_service_code_is_rejected_as_defense_in_depth() {
    ApiException ex =
        assertThrows(
            ApiException.class,
            () -> service.create(request(VIN, "F0001", 99), user(Role.ADMIN, null)));
    assertEquals(400, ex.status().value());
    verify(vehicles, never()).existsByVin(anyString());
  }

  @Test
  void unknown_dealer_code_yields_422() {
    when(dealers.findActiveByCode("F9999")).thenReturn(Optional.empty());
    ApiException ex =
        assertThrows(
            ApiException.class,
            () -> service.create(request(VIN, "F9999", 1), user(Role.ADMIN, null)));
    assertEquals("REFERENCED_DEALER_NOT_FOUND", ex.code());
    assertEquals(422, ex.status().value());
  }

  @Test
  void gestor_cannot_create_event_for_another_dealer() {
    ApiException ex =
        assertThrows(
            ApiException.class,
            () -> service.create(request(VIN, "F0001", 1), user(Role.GESTOR, DEALER_2)));
    assertEquals("ACCESS_OTHER_DEALER", ex.code());
  }

  @Test
  void duplicate_event_yields_409() {
    when(repo.existsDuplicate(
            eq(VIN), eq(UUID.fromString(DEALER_1)), eq("scheduled_maintenance"), any(), isNull()))
        .thenReturn(true);
    ApiException ex =
        assertThrows(
            ApiException.class,
            () -> service.create(request(VIN, "F0001", 1), user(Role.ADMIN, null)));
    assertEquals("SERVICE_EVENT_DUPLICATE", ex.code());
    assertEquals(409, ex.status().value());
  }

  @Test
  void reading_another_dealers_event_is_forbidden() {
    when(repo.findById(EVENT_ID)).thenReturn(Optional.of(event(DEALER_2)));
    ApiException ex =
        assertThrows(
            ApiException.class, () -> service.get(EVENT_ID, user(Role.ATENDENTE, DEALER_1)));
    assertEquals("ACCESS_OTHER_DEALER", ex.code());
  }

  @Test
  void listing_is_scoped_to_the_dealer_of_atendente() {
    UUID dealer = UUID.fromString(DEALER_1);
    when(repo.list(null, dealer, 50, 0)).thenReturn(List.of(event(DEALER_1)));
    when(repo.count(null, dealer)).thenReturn(1L);

    var page = service.list(null, 50, 0, user(Role.ATENDENTE, DEALER_1));

    assertEquals(1, page.total());
    verify(repo).list(null, dealer, 50, 0);
  }

  @Test
  void deleting_unknown_event_yields_404() {
    when(repo.findById(EVENT_ID)).thenReturn(Optional.empty());
    ApiException ex =
        assertThrows(ApiException.class, () -> service.delete(EVENT_ID, user(Role.ADMIN, null)));
    assertEquals("SERVICE_EVENT_NOT_FOUND", ex.code());
    verify(repo, never()).deleteById(any());
  }
}
