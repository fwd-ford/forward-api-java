package com.fwdford.forwardapi.error;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiExceptionTest {

  @Test
  void badRequest_uses_400_and_code() {
    ApiException ex = ApiException.badRequest("campo X invalido");
    assertEquals(HttpStatus.BAD_REQUEST, ex.status());
    assertEquals("bad_request", ex.code());
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
  void notFound_uses_404_and_mentions_resource() {
    ApiException ex = ApiException.notFound("customer");
    assertEquals(HttpStatus.NOT_FOUND, ex.status());
    assertTrue(ex.detail().toLowerCase().contains("customer"));
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
