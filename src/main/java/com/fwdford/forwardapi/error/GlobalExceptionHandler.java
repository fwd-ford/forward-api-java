// Global exception handler producing RFC 7807 Problem responses. Never leaks
// stack traces, internal paths, or technology details to clients.
// Handler global: produz respostas RFC 7807, sem vazar stack traces ou detalhes internos.
package com.fwdford.forwardapi.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest req) {
    ProblemDetail problem = ProblemDetail.forStatus(ex.status());
    problem.setTitle(ex.title());
    problem.setDetail(ex.detail());
    problem.setInstance(java.net.URI.create(req.getRequestURI()));
    problem.setProperty("code", ex.code());
    return ResponseEntity.status(ex.status()).body(problem);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {
    String detail =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
    if (detail.isBlank()) {
      detail = "Requisicao invalida.";
    }
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problem.setTitle("Requisicao invalida");
    problem.setDetail(detail);
    problem.setInstance(java.net.URI.create(req.getRequestURI()));
    problem.setProperty("code", "validation_failed");
    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleUnreadable(
      HttpMessageNotReadableException ex, HttpServletRequest req) {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problem.setTitle("Requisicao invalida");
    problem.setDetail("Corpo da requisicao malformado ou ausente.");
    problem.setInstance(java.net.URI.create(req.getRequestURI()));
    problem.setProperty("code", "malformed_body");
    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneric(
      Exception ex, HttpServletRequest req, WebRequest webReq) {
    log.error("unhandled error path={} msg={}", req.getRequestURI(), ex.getMessage(), ex);
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    problem.setTitle("Erro interno");
    problem.setDetail("Tente novamente em instantes.");
    problem.setInstance(java.net.URI.create(req.getRequestURI()));
    problem.setProperty("code", "internal");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
  }
}
