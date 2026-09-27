package com.fwdford.forwardapi.error;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiExceptionTest {

  @Test
  void badRequest_uses_400_and_code() {
    ApiException ex = ApiException.badRequest("campo X invalido");
    assertEquals(HttpStatus.BAD_REQUEST, ex.status());
    assertEquals("BAD_REQUEST", ex.code());
    assertTrue(ex.detail().contains("campo X"));
  }

  @Test
  void unauthorized_uses_401() {
    assertEquals(HttpStatus.UNAUTHORIZED, ApiException.unauthorized().status());
  }

  @Test
  void forbidden_uses_403() {
    assertEquals(HttpStatus.FORBIDDEN, ApiException.forbidden().status());
  }

  @Test
  void notFound_uses_404_and_keeps_code_and_detail() {
    ApiException ex = ApiException.notFound("CUSTOMER_NOT_FOUND", "Cliente não encontrado.");
    assertEquals(HttpStatus.NOT_FOUND, ex.status());
    assertEquals("CUSTOMER_NOT_FOUND", ex.code());
    assertTrue(ex.detail().contains("Cliente"));
    assertEquals("Recurso não encontrado", ex.title());
  }

  @Test
  void forbiddenOtherDealer_uses_403_and_specific_code() {
    ApiException ex = ApiException.forbiddenOtherDealer();
    assertEquals(HttpStatus.FORBIDDEN, ex.status());
    assertEquals("ACCESS_OTHER_DEALER", ex.code());
  }

  @Test
  void invalidCredentials_uses_401_and_generic_message() {
    ApiException ex = ApiException.invalidCredentials();
    assertEquals(HttpStatus.UNAUTHORIZED, ex.status());
    assertEquals("AUTH_INVALID_CREDENTIALS", ex.code());
    assertEquals("E-mail ou senha inválidos.", ex.detail());
  }

  @Test
  void conflict_and_unprocessable_use_409_and_422() {
    assertEquals(HttpStatus.CONFLICT, ApiException.conflict("X", "y").status());
    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ApiException.unprocessable("X", "y").status());
  }

  @Test
  void tooManyRequests_uses_429() {
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, ApiException.tooManyRequests().status());
  }

  @Test
  void internal_uses_500() {
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ApiException.internal().status());
  }
}
