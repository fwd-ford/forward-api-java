// Raised by JwtService when a bearer token cannot be trusted. The reason drives the
// problem code returned by the authentication entry point (expired vs invalid).
// Excecao de token invalido; o motivo define o codigo do erro 401.
package com.fwdford.forwardapi.security;

public class InvalidTokenException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** Why the token was rejected. */
  public enum Reason {
    EXPIRED,
    INVALID
  }

  private final Reason reason;

  private InvalidTokenException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public static InvalidTokenException expired() {
    return new InvalidTokenException(Reason.EXPIRED, "token expired");
  }

  public static InvalidTokenException invalid(String what) {
    return new InvalidTokenException(Reason.INVALID, "invalid token: " + what);
  }

  public Reason reason() {
    return reason;
  }
}
