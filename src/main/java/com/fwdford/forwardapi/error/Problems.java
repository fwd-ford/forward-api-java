// Single place that builds RFC 7807 problem details, so every error (controller advice,
// security entry point, access denied handler, rate limiter, /error) has the same shape:
//   type, title, status, detail, instance, code, timestamp, request_id [, errors]
// Fabrica unica de ProblemDetail: todo erro da API sai com o mesmo formato.
package com.fwdford.forwardapi.error;

import com.fwdford.forwardapi.web.WebAttrs;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

public final class Problems {

  public static final String TYPE_PREFIX = "urn:forward:problem:";

  private Problems() {}

  /** Builds a problem with the default pt-BR title for the status. */
  public static ProblemDetail build(
      HttpStatusCode status, String code, String detail, HttpServletRequest req) {
    return build(status, code, defaultTitle(status), detail, req);
  }

  public static ProblemDetail build(
      HttpStatusCode status, String code, String title, String detail, HttpServletRequest req) {
    ProblemDetail pd = ProblemDetail.forStatus(status);
    pd.setType(URI.create(TYPE_PREFIX + code.toLowerCase(Locale.ROOT).replace('_', '-')));
    pd.setTitle(title);
    pd.setDetail(detail);
    decorate(pd, code, req);
    return pd;
  }

  /** Adds instance, code, timestamp and request_id to an existing problem. */
  public static void decorate(ProblemDetail pd, String code, HttpServletRequest req) {
    if (req != null && pd.getInstance() == null) {
      try {
        pd.setInstance(URI.create(req.getRequestURI()));
      } catch (IllegalArgumentException ignored) {
        // Unparseable raw path: omit instance rather than fail while reporting an error.
      }
    }
    pd.setProperty("code", code);
    pd.setProperty("timestamp", Instant.now().toString());
    pd.setProperty("request_id", requestId(req));
  }

  static String requestId(HttpServletRequest req) {
    Object rid = req != null ? req.getAttribute(WebAttrs.REQUEST_ID) : null;
    if (rid instanceof String s && !s.isBlank()) {
      return s;
    }
    return MDC.get("request_id");
  }

  /** Default problem title in Portuguese for each HTTP status used by the API. */
  public static String defaultTitle(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> "Requisição inválida";
      case 401 -> "Não autenticado";
      case 403 -> "Acesso negado";
      case 404 -> "Recurso não encontrado";
      case 405 -> "Método não permitido";
      case 406 -> "Formato de resposta não suportado";
      case 409 -> "Conflito";
      case 413 -> "Corpo da requisição muito grande";
      case 415 -> "Tipo de mídia não suportado";
      case 422 -> "Entidade não processável";
      case 429 -> "Muitas requisições";
      case 500 -> "Erro interno";
      case 503 -> "Serviço indisponível";
      default -> {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        yield resolved != null ? resolved.getReasonPhrase() : "Erro";
      }
    };
  }
}
