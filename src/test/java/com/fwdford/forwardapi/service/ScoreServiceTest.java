package com.fwdford.forwardapi.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.ChurnScore;
import com.fwdford.forwardapi.model.Customer;
import com.fwdford.forwardapi.repository.CustomerRepository;
import com.fwdford.forwardapi.repository.ScoreRepository;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.security.Role;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Churn score reads follow the customer's dealer scope. */
class ScoreServiceTest {

  private static final String ID = "11111111-1111-1111-1111-111111111001";
  private static final String DEALER_1 = "d0000000-0000-4000-8000-000000000001";
  private static final String DEALER_2 = "d0000000-0000-4000-8000-000000000002";

  private ScoreRepository repo;
  private CustomerRepository customers;
  private ScoreService service;

  @BeforeEach
  void setup() {
    repo = Mockito.mock(ScoreRepository.class);
    customers = Mockito.mock(CustomerRepository.class);
    service = new ScoreService(repo, new CustomerService(customers));
    when(customers.findById(anyString()))
        .thenReturn(
            Optional.of(
                new Customer(ID, "João", null, null, null, null, true, OffsetDateTime.now())));
    ChurnScore s =
        new ChurnScore(ID, ID, null, "v1.0", "esquecido", 0.78, 0.82, OffsetDateTime.now());
    when(repo.findCurrentByCustomer(anyString())).thenReturn(Optional.of(s));
  }

  private static AuthenticatedUser user(Role role, String dealerId) {
    return new AuthenticatedUser(
        "ad000000-0000-4000-8000-000000000099", "u@forward.dev", "U", role, dealerId);
  }

  @Test
  void admin_reads_scores() {
    assertEquals(0.78, service.getCurrent(ID, user(Role.ADMIN, null)).churnProbability());
  }

  @Test
  void atendente_reads_scores_of_own_dealer_customers() {
    when(customers.isLinkedToDealer(ID, DEALER_1)).thenReturn(true);
    assertDoesNotThrow(() -> service.getCurrent(ID, user(Role.ATENDENTE, DEALER_1)));
  }

  @Test
  void gestor_cannot_read_scores_of_other_dealer_customers() {
    when(customers.isLinkedToDealer(ID, DEALER_2)).thenReturn(false);
    ApiException ex =
        assertThrows(ApiException.class, () -> service.getCurrent(ID, user(Role.GESTOR, DEALER_2)));
    assertEquals("ACCESS_OTHER_DEALER", ex.code());
    verify(repo, never()).findCurrentByCustomer(anyString());
  }

  @Test
  void unknown_customer_yields_customer_not_found() {
    when(customers.findById(anyString())).thenReturn(Optional.empty());
    ApiException ex =
        assertThrows(ApiException.class, () -> service.getCurrent(ID, user(Role.ADMIN, null)));
    assertEquals("CUSTOMER_NOT_FOUND", ex.code());
  }

  @Test
  void missing_score_yields_score_not_found() {
    when(repo.findCurrentByCustomer(anyString())).thenReturn(Optional.empty());
    ApiException ex =
        assertThrows(ApiException.class, () -> service.getCurrent(ID, user(Role.ADMIN, null)));
    assertEquals("SCORE_NOT_FOUND", ex.code());
    assertEquals(404, ex.status().value());
  }

  @Test
  void service_principal_reads_scores() {
    assertDoesNotThrow(() -> service.getCurrent(ID, AuthenticatedUser.service()));
  }
}
