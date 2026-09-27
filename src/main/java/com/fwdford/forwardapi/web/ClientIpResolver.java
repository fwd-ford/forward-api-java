// Client IP used for rate limiting and audit. It is the servlet container's remote address:
// behind a reverse proxy (Render) set server.forward-headers-strategy=native so Tomcat's
// RemoteIpValve replaces it with the address from X-Forwarded-For, trusting that header only
// when it comes from an internal proxy. Client-supplied headers are never read here.
// IP do cliente para rate limit e auditoria: endereco remoto resolvido pelo container.
package com.fwdford.forwardapi.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

  private static final Pattern IP_LITERAL = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

  public String resolve(HttpServletRequest req) {
    return req.getRemoteAddr();
  }

  /** Returns the IP only when it is a plain IPv4/IPv6 literal (safe for an INET column). */
  public static String asInetLiteral(String ip) {
    return ip != null && IP_LITERAL.matcher(ip).matches() ? ip : null;
  }
}
