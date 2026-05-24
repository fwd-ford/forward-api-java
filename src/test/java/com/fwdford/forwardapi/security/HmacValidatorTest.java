package com.fwdford.forwardapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HmacValidatorTest {

  private static final String SECRET = "test-secret-do-not-use-in-prod";
  private static final long NOW = 1748121600L; // arbitrary, treated as "current" in tests
  private static final byte[] BODY =
      "{\"event\":\"churn_rescored\",\"customer_id\":\"abc\"}".getBytes(StandardCharsets.UTF_8);

  @Test
  void verifyAcceptsFreshValidSignature() {
    HmacValidator v = new HmacValidator(SECRET);
    String sig = v.sign(NOW, "POST", "/api/v1/webhooks/churn", BODY);
    assertTrue(v.verify(String.valueOf(NOW), sig, "POST", "/api/v1/webhooks/churn", BODY, NOW));
  }

  @Test
  void verifyAcceptsSignatureUpToMaxSkew() {
    HmacValidator v = new HmacValidator(SECRET);
    String sig = v.sign(NOW, "POST", "/api/v1/webhooks/churn", BODY);
    long inWindow = NOW + HmacValidator.DEFAULT_MAX_SKEW_SECONDS;
    assertTrue(
        v.verify(String.valueOf(NOW), sig, "POST", "/api/v1/webhooks/churn", BODY, inWindow));
  }

  @Test
  void verifyRejectsExpiredTimestamp() {
    HmacValidator v = new HmacValidator(SECRET);
    String sig = v.sign(NOW, "POST", "/api/v1/webhooks/churn", BODY);
    long replayed = NOW + HmacValidator.DEFAULT_MAX_SKEW_SECONDS + 1;
    assertFalse(
        v.verify(String.valueOf(NOW), sig, "POST", "/api/v1/webhooks/churn", BODY, replayed));
  }

  @Test
  void verifyRejectsFutureTimestampBeyondSkew() {
    HmacValidator v = new HmacValidator(SECRET);
    String sig = v.sign(NOW, "POST", "/api/v1/webhooks/churn", BODY);
    long earlyServer = NOW - HmacValidator.DEFAULT_MAX_SKEW_SECONDS - 1;
    assertFalse(
        v.verify(String.valueOf(NOW), sig, "POST", "/api/v1/webhooks/churn", BODY, earlyServer));
  }

  @Test
  void verifyRejectsTamperedBody() {
    HmacValidator v = new HmacValidator(SECRET);
    String sig = v.sign(NOW, "POST", "/api/v1/webhooks/churn", BODY);
    byte[] tampered = "{\"event\":\"PWNED\"}".getBytes(StandardCharsets.UTF_8);
    assertFalse(
        v.verify(String.valueOf(NOW), sig, "POST", "/api/v1/webhooks/churn", tampered, NOW));
  }

  @Test
  void verifyRejectsTamperedPath() {
    HmacValidator v = new HmacValidator(SECRET);
    String sig = v.sign(NOW, "POST", "/api/v1/webhooks/churn", BODY);
    assertFalse(v.verify(String.valueOf(NOW), sig, "POST", "/api/v1/admin/customers", BODY, NOW));
  }

  @Test
  void verifyRejectsTamperedMethod() {
    HmacValidator v = new HmacValidator(SECRET);
    String sig = v.sign(NOW, "POST", "/api/v1/webhooks/churn", BODY);
    assertFalse(v.verify(String.valueOf(NOW), sig, "DELETE", "/api/v1/webhooks/churn", BODY, NOW));
  }

  @Test
  void verifyRejectsBitFlippedSignature() {
    HmacValidator v = new HmacValidator(SECRET);
    String sig = v.sign(NOW, "POST", "/api/v1/webhooks/churn", BODY);
    String flipped = flipLastHexChar(sig);
    assertFalse(
        v.verify(String.valueOf(NOW), flipped, "POST", "/api/v1/webhooks/churn", BODY, NOW));
  }

  @Test
  void verifyRejectsMissingHeaders() {
    HmacValidator v = new HmacValidator(SECRET);
    assertFalse(v.verify(null, "deadbeef", "POST", "/x", new byte[0], NOW));
    assertFalse(v.verify("0", null, "POST", "/x", new byte[0], NOW));
    assertFalse(v.verify("0", "deadbeef", null, "/x", new byte[0], NOW));
    assertFalse(v.verify("0", "deadbeef", "POST", null, new byte[0], NOW));
  }

  @Test
  void verifyRejectsNonNumericTimestamp() {
    HmacValidator v = new HmacValidator(SECRET);
    assertFalse(v.verify("notanumber", "deadbeef", "POST", "/x", new byte[0], NOW));
  }

  @Test
  void verifyIsCaseInsensitiveOnSignatureHex() {
    HmacValidator v = new HmacValidator(SECRET);
    String sig = v.sign(NOW, "POST", "/api/v1/webhooks/churn", BODY);
    assertTrue(
        v.verify(
            String.valueOf(NOW), sig.toUpperCase(), "POST", "/api/v1/webhooks/churn", BODY, NOW));
  }

  @Test
  void signIsDeterministic() {
    HmacValidator v = new HmacValidator(SECRET);
    String a = v.sign(NOW, "POST", "/x", BODY);
    String b = v.sign(NOW, "POST", "/x", BODY);
    assertEquals(a, b);
  }

  @Test
  void constructorRejectsBlankSecret() {
    assertThrows(IllegalArgumentException.class, () -> new HmacValidator(""));
    assertThrows(IllegalArgumentException.class, () -> new HmacValidator("   "));
    assertThrows(IllegalArgumentException.class, () -> new HmacValidator(null));
  }

  @Test
  void constructorRejectsNonPositiveSkew() {
    assertThrows(IllegalArgumentException.class, () -> new HmacValidator(SECRET, 0));
    assertThrows(IllegalArgumentException.class, () -> new HmacValidator(SECRET, -10));
  }

  private static String flipLastHexChar(String hex) {
    char last = hex.charAt(hex.length() - 1);
    char flipped = last == '0' ? '1' : '0';
    return hex.substring(0, hex.length() - 1) + flipped;
  }
}
