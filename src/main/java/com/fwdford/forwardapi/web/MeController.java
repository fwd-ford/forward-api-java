// Returns the current subject and role as parsed from the JWT or API key.
// Retorna o sujeito e papel atuais, conforme parseado do JWT ou X-API-Key.
package com.fwdford.forwardapi.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Me", description = "Current authenticated subject.")
public class MeController {

  @GetMapping("/me")
  @Operation(
      summary = "Get current subject",
      description =
          "Returns the `sub` and `role` parsed from the validated JWT or X-API-Key. "
              + "Useful to confirm auth is working end-to-end.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Subject and role of the authenticated caller",
        content =
            @Content(
                schema =
                    @Schema(
                        example = "{\"sub\":\"f7b3...\",\"role\":\"admin\"}",
                        type = "object"))),
    @ApiResponse(
        responseCode = "401",
        description = "Missing or invalid token",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public Map<String, Object> me(HttpServletRequest req) {
    AuthPrincipal p = (AuthPrincipal) req.getAttribute(WebAttrs.PRINCIPAL);
    Map<String, Object> body = new java.util.HashMap<>();
    body.put("sub", p != null ? p.sub() : null);
    body.put("role", p != null ? p.role() : null);
    return body;
  }
}
