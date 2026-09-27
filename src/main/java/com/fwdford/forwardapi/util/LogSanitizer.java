// Neutralizes user-controlled values before they reach a log line (CWE-117 log injection):
// anything outside letters, digits, punctuation, symbols and plain spaces (CR, LF, tabs,
// control and line separator characters) becomes '_', and the value is capped so a client
// cannot flood the logs. Use it for anything that comes from the request (path, headers,
// IP) when writing it with SLF4J.
// Sanitiza valores vindos do cliente antes de logar (evita log injection).
package com.fwdford.forwardapi.util;

public final class LogSanitizer {

  /** Longest value written to the logs; longer input is truncated. */
  public static final int MAX_LENGTH = 200;

  private LogSanitizer() {}

  public static String sanitize(String value) {
    if (value == null) {
      return "null";
    }
    // Allow list: every character that is not a letter, digit, punctuation, symbol or space.
    String cleaned = value.replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{S} ]", "_");
    return cleaned.length() <= MAX_LENGTH ? cleaned : cleaned.substring(0, MAX_LENGTH) + "...";
  }
}
