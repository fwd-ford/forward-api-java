package com.fwdford.forwardapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.ServiceEvent;
import com.fwdford.forwardapi.model.Vehicle;
import com.fwdford.forwardapi.repository.ServiceEventRepository;
import com.fwdford.forwardapi.repository.VehicleRepository;
import com.fwdford.forwardapi.web.CreateServiceEventRequest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ServiceEventServiceTest {

  private static final String VIN = "9BFZZZ5SZJB000001";
  private static final String DEALER_CODE = "F0001";
  private static final OffsetDateTime SERVICE_DATE = OffsetDateTime.parse("2026-05-23T10:00:00Z");

  private ServiceEventRepository repo;
  private VehicleRepository vehicleRepo;
  private ServiceEventService service;

  @BeforeEach
  void setup() {
    repo = Mockito.mock(ServiceEventRepository.class);
    vehicleRepo = Mockito.mock(VehicleRepository.class);
    service = new ServiceEventService(repo, vehicleRepo);
  }

  @Test
  void happy_path_creates_service_event() {
    UUID dealerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    Vehicle vehicle =
        new Vehicle(VIN, "cust-1", "Ka", 2018, "SE 1.0", "Prata", false, LocalDate.now(), null);
    ServiceEvent expected =
        new ServiceEvent(
            "11111111-1111-1111-1111-111111111111",
            VIN,
            dealerId.toString(),
            "scheduled_maintenance",
            "scheduled",
            SERVICE_DATE,
            50000,
            3,
            "dealer_app");

    when(vehicleRepo.findByVin(VIN)).thenReturn(Optional.of(vehicle));
    when(repo.findDealerIdByCode(DEALER_CODE)).thenReturn(Optional.of(dealerId));
    when(repo.insert(
            eq(VIN),
            eq(dealerId),
            eq("scheduled_maintenance"),
            eq(SERVICE_DATE),
            eq(50000),
            eq(3),
            eq("dealer_app")))
        .thenReturn(expected);

    CreateServiceEventRequest req =
        new CreateServiceEventRequest(VIN, DEALER_CODE, 1, 3, 50000, SERVICE_DATE, "dealer_app");
    ServiceEvent actual = service.create(req);

    assertEquals(expected, actual);
  }

  @Test
  void missing_vin_yields_not_found_and_does_not_insert() {
    when(vehicleRepo.findByVin(anyString())).thenReturn(Optional.empty());

    CreateServiceEventRequest req =
        new CreateServiceEventRequest(VIN, DEALER_CODE, 1, 3, 50000, SERVICE_DATE, "dealer_app");
    ApiException ex = assertThrows(ApiException.class, () -> service.create(req));

    assertEquals("not_found", ex.code());
    verify(repo, never())
        .insert(anyString(), any(), anyString(), any(), any(), anyInt(), anyString());
  }

  @Test
  void invalid_vin_yields_bad_request_and_does_not_touch_repos() {
    CreateServiceEventRequest req =
        new CreateServiceEventRequest(
            "SHORT", DEALER_CODE, 1, 3, 50000, SERVICE_DATE, "dealer_app");
    ApiException ex = assertThrows(ApiException.class, () -> service.create(req));

    assertEquals("bad_request", ex.code());
    verify(vehicleRepo, never()).findByVin(anyString());
  }

  @Test
  void invalid_service_code_triggers_defense_in_depth_and_does_not_touch_repos() {
    CreateServiceEventRequest req =
        new CreateServiceEventRequest(VIN, DEALER_CODE, 99, 3, 50000, SERVICE_DATE, "dealer_app");

    assertThrows(IllegalStateException.class, () -> service.create(req));

    verify(vehicleRepo, never()).findByVin(anyString());
    verify(repo, never()).findDealerIdByCode(anyString());
  }

  @Test
  void missing_dealer_yields_not_found() {
    Vehicle vehicle =
        new Vehicle(VIN, "cust-1", "Ka", 2018, "SE 1.0", "Prata", false, LocalDate.now(), null);
    when(vehicleRepo.findByVin(VIN)).thenReturn(Optional.of(vehicle));
    when(repo.findDealerIdByCode(anyString())).thenReturn(Optional.empty());

    CreateServiceEventRequest req =
        new CreateServiceEventRequest(VIN, "F9999", 1, 3, 50000, SERVICE_DATE, "dealer_app");
    ApiException ex = assertThrows(ApiException.class, () -> service.create(req));

    assertEquals("not_found", ex.code());
    verify(repo, never())
        .insert(anyString(), any(), anyString(), any(), any(), anyInt(), anyString());
  }
}
