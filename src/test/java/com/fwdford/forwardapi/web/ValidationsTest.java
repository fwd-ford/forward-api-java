package com.fwdford.forwardapi.web;

import static org.junit.jupiter.api.Assertions.*;

import com.fwdford.forwardapi.error.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationsTest {

  @Test
  void validateUuid_accepts_valid_uuid() {
    String uuid = "2ddd2b47-9a80-4a0c-8c0a-8ee35d6f8b10";
    assertEquals(uuid, Validations.validateUuid("id", uuid));
  }

  @Test
  void validateUuid_trims_whitespace() {
    String uuid = "2ddd2b47-9a80-4a0c-8c0a-8ee35d6f8b10";
    assertEquals(uuid, Validations.validateUuid("id", "  " + uuid + "\t"));
  }

  @Test
  void validateUuid_rejects_bad_format() {
    assertThrows(ApiException.class, () -> Validations.validateUuid("id", "not-a-uuid"));
  }

  @Test
  void validateUuid_rejects_empty() {
    assertThrows(ApiException.class, () -> Validations.validateUuid("id", ""));
  }

  @Test
  void validateVin_uppercases_and_accepts() {
    String vin = "1fmcu0gx7jua12345";
    assertEquals("1FMCU0GX7JUA12345", Validations.validateVin(vin));
  }

  @Test
  void validateVin_rejects_wrong_length() {
    assertThrows(ApiException.class, () -> Validations.validateVin("1FMCU0GX"));
  }

  @Test
  void validateVin_rejects_forbidden_chars() {
    String vinWithI = "1FMCU0GXIJUA12345";
    assertThrows(ApiException.class, () -> Validations.validateVin(vinWithI));
  }

  @Test
  void validateLimit_returns_default_when_blank() {
    assertEquals(50, Validations.validateLimit("", 50, 200));
    assertEquals(50, Validations.validateLimit(null, 50, 200));
  }

  @Test
  void validateLimit_clamps_to_max() {
    assertEquals(200, Validations.validateLimit("5000", 50, 200));
  }

  @Test
  void validateLimit_rejects_zero_or_negative() {
    assertThrows(ApiException.class, () -> Validations.validateLimit("0", 50, 200));
    assertThrows(ApiException.class, () -> Validations.validateLimit("-3", 50, 200));
  }

  @Test
  void validateLimit_rejects_non_integer() {
    assertThrows(ApiException.class, () -> Validations.validateLimit("abc", 50, 200));
  }

  @Test
  void validateEnum_returns_empty_on_blank() {
    assertEquals("", Validations.validateEnum("status", "", List.of("a", "b")));
    assertEquals("", Validations.validateEnum("status", null, List.of("a", "b")));
  }

  @Test
  void validateEnum_accepts_whitelisted_value() {
    assertEquals("new", Validations.validateEnum("status", "new", List.of("new", "lost")));
  }

  @Test
  void validateEnum_rejects_unknown_value() {
    assertThrows(
        ApiException.class,
        () -> Validations.validateEnum("status", "weird", List.of("new", "lost")));
  }
}
