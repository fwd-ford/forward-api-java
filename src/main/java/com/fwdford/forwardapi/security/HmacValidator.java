// HMAC-SHA256 request signature validator. Stronger than X-API-Key because it
// proves the request body has not been tampered with and replay-protects via
// a timestamp window.
//
// Wire protocol (caller computes, server verifies):
//   canonical  = "<timestamp>:<METHOD>:<path>:<sha256(body)>"
//   signature  = HMAC_SHA256(secret, canonical), hex lowercase
// Headers carried by the caller:
//   X-Timestamp: Unix epoch seconds
//   X-Signature: hex(HMAC_SHA256)
//
// Validador de assinatura HMAC-SHA256 do request. Mais forte que X-API-Key
// porque prova integridade do body e protege contra replay via timestamp.
package com.fwdford.forwardapi.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacValidator {

  /** Allowed clock skew between caller and server. */
  public static final long DEFAULT_MAX_SKEW_SECONDS = 300L; // 5 minutes

  private static final String ALGORITHM = "HmacSHA256";

  private final String secret;
  private final long maxSkewSeconds;

  public HmacValidator(String secret) {
    this(secret, DEFAULT_MAX_SKEW_SECONDS);
  }

  public HmacValidator(String secret, long maxSkewSeconds) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalArgumentException("HMAC secret must not be blank");
    }
    if (maxSkewSeconds < 1) {
      throw new IllegalArgumentException("maxSkewSeconds must be positive");
    }
    this.secret = secret;
    this.maxSkewSeconds = maxSkewSeconds;
  }

  /**
   * Verify that the supplied signature matches the canonical request and the timestamp is fresh.
   *
   * @param timestampHeader value of the X-Timestamp header (Unix epoch seconds as string)
   * @param signatureHeader value of the X-Signature header (hex-encoded HMAC-SHA256)
   * @param method HTTP method, upper-case
   * @param path request path including query string
   * @param body raw request body (may be empty)
   * @param now current server time in epoch seconds
   * @return true if the signature is valid AND the timestamp is within the allowed skew
   */
  public boolean verify(
      String timestampHeader,
      String signatureHeader,
      String method,
      String path,
      byte[] body,
      long now) {
    if (timestampHeader == null || signatureHeader == null || method == null || path == null) {
      return false;
    }
    final long ts;
    try {
      ts = Long.parseLong(timestampHeader.trim());
    } catch (NumberFormatException ex) {
      return false;
    }
    if (Math.abs(now - ts) > maxSkewSeconds) {
      return false;
    }
    String expected = sign(ts, method, path, body == null ? new byte[0] : body);
    return constantTimeEquals(expected, signatureHeader.trim().toLowerCase());
  }

  /**
   * Compute the canonical signature for a request. Exposed for callers (tests, N8N client docs).
   */
  public String sign(long timestamp, String method, String path, byte[] body) {
    String bodyHash = hexSha256(body);
    String canonical = timestamp + ":" + method.toUpperCase() + ":" + path + ":" + bodyHash;
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      byte[] sig = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
      return toHex(sig);
    } catch (NoSuchAlgorithmException | java.security.InvalidKeyException ex) {
      throw new IllegalStateException("HMAC unavailable", ex);
    }
  }

  private static String hexSha256(byte[] body) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return toHex(md.digest(body));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  private static String toHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  /** Constant-time comparison to avoid timing oracles on signature checks. */
  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null || a.length() != b.length()) {
      return false;
    }
    int diff = 0;
    for (int i = 0; i < a.length(); i++) {
      diff |= a.charAt(i) ^ b.charAt(i);
    }
    return diff == 0;
  }
}
