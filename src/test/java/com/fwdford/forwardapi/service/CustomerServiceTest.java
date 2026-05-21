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
  void user_can_only_read_itself() {
    assertDoesNotThrow(() -> service.get(TARGET_ID, TARGET_ID, "user"));
  }

  @Test
  void user_cannot_read_another_customer() {
    ApiException ex =
        assertThrows(ApiException.class, () -> service.get(TARGET_ID, OTHER_ID, "user"));
    assertEquals("forbidden", ex.code());
  }

  @Test
  void unknown_role_is_forbidden() {
    assertThrows(ApiException.class, () -> service.get(TARGET_ID, TARGET_ID, "hacker"));
  }

  @Test
  void missing_customer_yields_not_found() {
    when(repo.findById(anyString())).thenReturn(Optional.empty());
    ApiException ex =
        assertThrows(ApiException.class, () -> service.get(TARGET_ID, TARGET_ID, "admin"));
    assertEquals("not_found", ex.code());
  }
}
