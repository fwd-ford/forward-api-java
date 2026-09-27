// Resolves the client IP used for rate limiting and audit. Behind Fly.io every request
// arrives from the edge proxy, so a trusted header (TRUSTED_CLIENT_IP_HEADER, e.g.
// Fly-Client-IP, which the proxy overwrites) can be configured. Without it the socket
// address is used; client-supplied X-Forwarded-For is never trusted blindly.
// Resolve o IP do cliente para rate limit e auditoria (header confiavel opcional).
package com.fwdford.forwardapi.web;

import com.fwdford.forwardapi.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

  private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

  private final String trustedHeader;

  public ClientIpResolver(AppProperties props) {
    String header = props.trustedClientIpHeader();
    this.trustedHeader = header == null || header.isBlank() ? null : header.trim();
  }

  public String resolve(HttpServletRequest req) {
    if (trustedHeader != null) {
      String value = req.getHeader(trustedHeader);
      if (value != null) {
        String candidate = value.split(",", 2)[0].trim();
        if (IP_LITERAL.matcher(candidate).matches()) {
          return candidate;
        }
      }
    }
    return req.getRemoteAddr();
  }

  /** Returns the IP only when it is a plain IPv4/IPv6 literal (safe for an INET column). */
  public static String asInetLiteral(String ip) {
    return ip != null && IP_LITERAL.matcher(ip).matches() ? ip : null;
  }
}
