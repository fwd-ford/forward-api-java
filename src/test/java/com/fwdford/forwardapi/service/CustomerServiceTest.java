package com.fwdford.forwardapi.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.Customer;
import com.fwdford.forwardapi.repository.CustomerRepository;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.security.Role;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Dealer scoping rules for customer reads (unit level, repository mocked). */
class CustomerServiceTest {

  private static final String CUSTOMER_ID = "11111111-1111-1111-1111-111111111001";
  private static final String DEALER_1 = "d0000000-0000-4000-8000-000000000001";
  private static final String DEALER_2 = "d0000000-0000-4000-8000-000000000002";

  private CustomerRepository repo;
  private CustomerService service;

  @BeforeEach
  void setup() {
    repo = Mockito.mock(CustomerRepository.class);
    service = new CustomerService(repo);
    Customer c =
        new Customer(
            CUSTOMER_ID, "João da Silva", null, null, null, null, false, OffsetDateTime.now());
    when(repo.findById(anyString())).thenReturn(Optional.of(c));
  }

  private static AuthenticatedUser user(Role role, String dealerId) {
    return new AuthenticatedUser(
        "ad000000-0000-4000-8000-000000000099", "u@forward.dev", "U", role, dealerId);
  }

  @Test
  void admin_reads_any_customer_without_scope_check() {
    Customer c = service.get(CUSTOMER_ID, user(Role.ADMIN, null));
    assertEquals(CUSTOMER_ID, c.id());
    verify(repo, never()).isLinkedToDealer(anyString(), anyString());
  }

  @Test
  void service_principal_reads_any_customer() {
    assertDoesNotThrow(() -> service.get(CUSTOMER_ID, AuthenticatedUser.service()));
  }

  @Test
  void atendente_reads_customer_linked_to_own_dealer() {
    when(repo.isLinkedToDealer(CUSTOMER_ID, DEALER_1)).thenReturn(true);
    assertDoesNotThrow(() -> service.get(CUSTOMER_ID, user(Role.ATENDENTE, DEALER_1)));
  }

  @Test
  void gestor_reads_customer_linked_to_own_dealer() {
    when(repo.isLinkedToDealer(CUSTOMER_ID, DEALER_1)).thenReturn(true);
    assertDoesNotThrow(() -> service.get(CUSTOMER_ID, user(Role.GESTOR, DEALER_1)));
  }

  @Test
  void atendente_from_other_dealer_is_forbidden() {
    when(repo.isLinkedToDealer(CUSTOMER_ID, DEALER_2)).thenReturn(false);
    ApiException ex =
        assertThrows(
            ApiException.class, () -> service.get(CUSTOMER_ID, user(Role.ATENDENTE, DEALER_2)));
    assertEquals("ACCESS_OTHER_DEALER", ex.code());
    assertEquals(403, ex.status().value());
  }

  @Test
  void gestor_from_other_dealer_is_forbidden() {
    when(repo.isLinkedToDealer(CUSTOMER_ID, DEALER_2)).thenReturn(false);
    ApiException ex =
        assertThrows(
            ApiException.class, () -> service.get(CUSTOMER_ID, user(Role.GESTOR, DEALER_2)));
    assertEquals("ACCESS_OTHER_DEALER", ex.code());
  }

  @Test
  void dealer_scoped_user_without_dealer_is_forbidden() {
    ApiException ex =
        assertThrows(
            ApiException.class, () -> service.get(CUSTOMER_ID, user(Role.ATENDENTE, null)));
    assertEquals("ACCESS_OTHER_DEALER", ex.code());
  }

  @Test
  void missing_customer_yields_not_found_before_scope_check() {
    when(repo.findById(anyString())).thenReturn(Optional.empty());
    ApiException ex =
        assertThrows(
            ApiException.class, () -> service.get(CUSTOMER_ID, user(Role.ATENDENTE, DEALER_1)));
    assertEquals("CUSTOMER_NOT_FOUND", ex.code());
    assertEquals(404, ex.status().value());
    verify(repo, never()).isLinkedToDealer(anyString(), anyString());
  }

  @Test
  void role_permissions_match_matrix() {
    assertTrue(Role.ADMIN.permissions().contains("users:manage"));
    assertFalse(Role.GESTOR.permissions().contains("users:manage"));
    assertTrue(Role.GESTOR.permissions().contains("service-events:write"));
    assertFalse(Role.ATENDENTE.permissions().contains("service-events:write"));
    assertTrue(Role.ATENDENTE.dealerScoped());
    assertFalse(Role.ADMIN.dealerScoped());
  }

  @Test
  void parse_user_role_rejects_service_and_unknown() {
    assertEquals(Optional.of(Role.GESTOR), Role.parseUserRole("gestor"));
    assertEquals(Optional.empty(), Role.parseUserRole("SERVICE"));
    assertEquals(Optional.empty(), Role.parseUserRole("root"));
    assertEquals(Optional.empty(), Role.parseUserRole(null));
  }
}
