// Replaces Spring Boot's BasicErrorController so container-level errors (requests
// rejected before reaching a controller, exceptions escaping filters) also answer with
// application/problem+json instead of the default JSON or HTML error page.
// Substitui o /error padrao: erros do container tambem saem como problem+json.
package com.fwdford.forwardapi.error;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
public class ProblemErrorController implements ErrorController {

  // Error dispatches keep the original HTTP method, so every method is listed explicitly.
  // The API is stateless (no cookies), so there is no CSRF surface on this handler.
  @RequestMapping(
      value = "/error",
      method = {
        RequestMethod.GET,
        RequestMethod.HEAD,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.PATCH,
        RequestMethod.DELETE,
        RequestMethod.OPTIONS
      })
  public ResponseEntity<ProblemDetail> error(HttpServletRequest req) {
    Object rawStatus = req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    if (rawStatus instanceof Integer code) {
      HttpStatus resolved = HttpStatus.resolve(code);
      if (resolved != null) {
        status = resolved;
      }
    }
    ProblemDetail pd =
        Problems.build(
            status,
            GlobalExceptionHandler.defaultCode(status),
            GlobalExceptionHandler.defaultDetail(status),
            req);
    Object originalUri = req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
    if (originalUri instanceof String uri) {
      try {
        pd.setInstance(URI.create(uri));
      } catch (IllegalArgumentException ignored) {
        // Keep /error as instance when the original URI cannot be parsed.
      }
    }
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
  }
}
