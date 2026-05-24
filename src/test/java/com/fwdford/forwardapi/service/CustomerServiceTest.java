package com.fwdford.forwardapi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.Customer;
import com.fwdford.forwardapi.repository.CustomerRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CustomerServiceTest {

  private CustomerRepository repo;
  private CustomerService service;

  private static final String TARGET_ID = "2ddd2b47-9a80-4a0c-8c0a-8ee35d6f8b10";
  private static final String OTHER_ID = "9f1d7b6a-1234-4f55-89ab-abcdef012345";

  @BeforeEach
  void setup() {
    repo = Mockito.mock(CustomerRepository.class);
    service = new CustomerService(repo);
    Customer c =
        new Customer(TARGET_ID, "Jota", null, null, null, null, false, OffsetDateTime.now());
    when(repo.findById(anyString())).thenReturn(Optional.of(c));
  }

  // Sprint 1 relaxed RBAC: any authenticated caller (any role) may read any customer.
  // The tests below pin the behavior so the mobile Lead Detail flow keeps working.
  // RBAC Sprint 1 relaxado: qualquer caller autenticado le qualquer customer.

  @Test
  void admin_can_read_any_customer() {
    Customer c = service.get(TARGET_ID, "some-admin-sub", "admin");
    assertEquals(TARGET_ID, c.id());
  }

  @Test
  void analyst_can_read_any_customer() {
    assertDoesNotThrow(() -> service.get(TARGET_ID, "an-analyst", "analyst"));
  }

  @Test
  void dealer_can_read_any_customer() {
    assertDoesNotThrow(() -> service.get(TARGET_ID, "a-dealer", "dealer"));
  }

  @Test
  void end_user_can_read_itself() {
    assertDoesNotThrow(() -> service.get(TARGET_ID, TARGET_ID, "user"));
  }

  @Test
  void end_user_can_read_other_customer_under_sprint1_relaxed_rbac() {
    assertDoesNotThrow(() -> service.get(TARGET_ID, OTHER_ID, "user"));
  }

  @Test
  void any_authenticated_role_is_allowed() {
    assertDoesNotThrow(() -> service.get(TARGET_ID, "mobile-app-sub", "mobile_app"));
  }

  @Test
  void caller_without_role_claim_is_allowed_if_sub_is_present() {
    assertDoesNotThrow(() -> service.get(TARGET_ID, "service-account", null));
  }

  @Test
  void null_sub_is_forbidden() {
    ApiException ex = assertThrows(ApiException.class, () -> service.get(TARGET_ID, null, "admin"));
    assertEquals("forbidden", ex.code());
  }

  @Test
  void blank_sub_is_forbidden() {
    ApiException ex = assertThrows(ApiException.class, () -> service.get(TARGET_ID, "  ", "admin"));
    assertEquals("forbidden", ex.code());
  }

  @Test
  void missing_customer_yields_not_found() {
    when(repo.findById(anyString())).thenReturn(Optional.empty());
    ApiException ex =
        assertThrows(ApiException.class, () -> service.get(TARGET_ID, TARGET_ID, "admin"));
    assertEquals("not_found", ex.code());
  }
}
