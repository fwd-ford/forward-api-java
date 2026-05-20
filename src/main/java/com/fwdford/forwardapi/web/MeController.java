// Returns the current subject and role as parsed from the JWT or API key.
// Retorna o sujeito e papel atuais, conforme parseado do JWT ou X-API-Key.
package com.fwdford.forwardapi.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MeController {

  @GetMapping("/me")
  public Map<String, Object> me(HttpServletRequest req) {
    AuthPrincipal p = (AuthPrincipal) req.getAttribute(WebAttrs.PRINCIPAL);
    Map<String, Object> body = new java.util.HashMap<>();
    body.put("sub", p != null ? p.sub() : null);
    body.put("role", p != null ? p.role() : null);
    return body;
  }
}
