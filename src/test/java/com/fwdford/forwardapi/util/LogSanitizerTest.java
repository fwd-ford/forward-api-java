package com.fwdford.forwardapi.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Log injection (CWE-117) protection. */
class LogSanitizerTest {

  @Test
  void line_breaks_cannot_forge_new_log_lines() {
    String forged = "/api/v1/leads\r\n2026-09-27 INFO fake_admin_login user=admin";
    String safe = LogSanitizer.sanitize(forged);
    assertFalse(safe.contains("\n"));
    assertFalse(safe.contains("\r"));
    assertTrue(safe.startsWith("/api/v1/leads__2026"));
  }

  @Test
  void other_control_characters_are_replaced() {
    assertEquals("a_b_c_d", LogSanitizer.sanitize("a\tb\u0000c\u001bd"));
    assertEquals("x_y", LogSanitizer.sanitize("x\u0085y"));
  }

  @Test
  void regular_text_and_accents_are_kept() {
    assertEquals(
        "/api/v1/leads/a1?status=new", LogSanitizer.sanitize("/api/v1/leads/a1?status=new"));
    assertEquals("concessionária São Paulo", LogSanitizer.sanitize("concessionária São Paulo"));
  }

  @Test
  void long_values_are_truncated() {
    String safe = LogSanitizer.sanitize("x".repeat(5000));
    assertEquals(LogSanitizer.MAX_LENGTH + 3, safe.length());
    assertTrue(safe.endsWith("..."));
  }

  @Test
  void vin_is_masked_except_the_last_six_characters() {
    assertEquals("***********000001", LogSanitizer.maskVin("9BFZZZ5SZJB000001"));
    assertFalse(LogSanitizer.maskVin("9BFZZZ5SZJB000001").contains("9BFZZZ"));
  }

  @Test
  void mask_handles_short_values_null_and_control_characters() {
    assertEquals("12345", LogSanitizer.mask("12345", 6));
    assertEquals("**345", LogSanitizer.mask("12345", 3));
    assertEquals("*****", LogSanitizer.mask("12345", 0));
    assertEquals("null", LogSanitizer.mask(null, 6));
    assertEquals("*********_forged", LogSanitizer.mask("AAAAAAAAA\nforged", 7));
  }

  @Test
  void null_is_rendered_explicitly() {
    assertEquals("null", LogSanitizer.sanitize(null));
  }
}
