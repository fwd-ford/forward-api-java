// Global exception handler producing RFC 7807 problems (application/problem+json).
// Extends ResponseEntityExceptionHandler so framework errors keep their correct status
// (400 malformed JSON / type mismatch, 404 unknown path, 405, 406, 413, 415) instead of
// collapsing into 500. Never leaks stack traces, SQL, internal paths or class names.
// Handler global RFC 7807: preserva o status correto dos erros do framework e nunca
// expoe detalhes internos.
package com.fwdford.forwardapi.error;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final PropertyNamingStrategies.NamingBase SNAKE_CASE =
      (PropertyNamingStrategies.NamingBase) PropertyNamingStrategies.SNAKE_CASE;

  /** Field-level validation error returned in the "errors" array. */
  public record FieldProblem(String field, String message) {}

  // ---------------------------------------------------------------------------
  // Application exceptions
  // ---------------------------------------------------------------------------

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ProblemDetail> handleApi(ApiException ex, HttpServletRequest req) {
    ProblemDetail pd = Problems.build(ex.status(), ex.code(), ex.title(), ex.detail(), req);
    return problem(ex.status(), pd);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest req) {
    ApiException api = ApiException.forbidden();
    return handleApi(api, req);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ProblemDetail> handleAuthentication(
      AuthenticationException ex, HttpServletRequest req) {
    return handleApi(ApiException.unauthorized(), req);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> handleDataIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest req) {
    log.warn(
        "data_integrity_violation path={} cause={}",
        req.getRequestURI(),
        ex.getMostSpecificCause().getClass().getSimpleName());
    ProblemDetail pd =
        Problems.build(
            HttpStatus.CONFLICT,
            "CONFLICT",
            "A operação conflita com o estado atual dos dados (registro duplicado ou em uso).",
            req);
    return problem(HttpStatus.CONFLICT, pd);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest req) {
    List<FieldProblem> errors =
        ex.getConstraintViolations().stream()
            .map(
                v ->
                    new FieldProblem(
                        jsonName(lastNode(v.getPropertyPath().toString())), v.getMessage()))
            .collect(Collectors.toList());
    return problem(HttpStatus.BAD_REQUEST, validationProblem(errors, req));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest req) {
    log.error("unhandled_error path={} type={}", req.getRequestURI(), ex.getClass().getName(), ex);
    ApiException api = ApiException.internal();
    return handleApi(api, req);
  }

  // ---------------------------------------------------------------------------
  // Spring MVC exceptions (status preserved, message translated to pt-BR)
  // ---------------------------------------------------------------------------

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldProblem> errors = new ArrayList<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      errors.add(new FieldProblem(jsonName(fe.getField()), fe.getDefaultMessage()));
    }
    ex.getBindingResult()
        .getGlobalErrors()
        .forEach(ge -> errors.add(new FieldProblem(ge.getObjectName(), ge.getDefaultMessage())));
    return handleExceptionInternal(
        ex, validationProblem(errors, servletRequest(request)), headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldProblem> errors = new ArrayList<>();
    ex.getParameterValidationResults()
        .forEach(
            r -> {
              String name = r.getMethodParameter().getParameterName();
              for (MessageSourceResolvable err : r.getResolvableErrors()) {
                errors.add(new FieldProblem(name, err.getDefaultMessage()));
              }
            });
    return handleExceptionInternal(
        ex,
        validationProblem(errors, servletRequest(request)),
        headers,
        HttpStatus.BAD_REQUEST,
        request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String code = "MALFORMED_JSON";
    String detail = "Corpo da requisição ausente ou com JSON malformado.";
    if (ex.getCause() instanceof MismatchedInputException mie && !mie.getPath().isEmpty()) {
      code = "INVALID_FIELD_VALUE";
      detail = "Valor inválido para o campo '" + jsonPath(mie.getPath()) + "'.";
    }
    ProblemDetail pd = Problems.build(status, code, detail, servletRequest(request));
    return handleExceptionInternal(ex, pd, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String name = ex.getPropertyName() != null ? ex.getPropertyName() : "parâmetro";
    ProblemDetail pd =
        Problems.build(
            HttpStatus.BAD_REQUEST,
            "TYPE_MISMATCH",
            "Valor inválido para o parâmetro '" + name + "'.",
            servletRequest(request));
    return handleExceptionInternal(ex, pd, headers, HttpStatus.BAD_REQUEST, request);
  }

  @Override
  protected ResponseEntity<Object> handleMissingServletRequestParameter(
      MissingServletRequestParameterException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail pd =
        Problems.build(
            status,
            "MISSING_PARAMETER",
            "Parâmetro obrigatório '" + ex.getParameterName() + "' ausente.",
            servletRequest(request));
    return handleExceptionInternal(ex, pd, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail pd =
        Problems.build(
            status,
            "METHOD_NOT_ALLOWED",
            "Método " + ex.getMethod() + " não é suportado por este recurso.",
            servletRequest(request));
    return handleExceptionInternal(ex, pd, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail pd =
        Problems.build(
            status,
            "UNSUPPORTED_MEDIA_TYPE",
            "Content-Type não suportado. Envie o corpo como application/json.",
            servletRequest(request));
    return handleExceptionInternal(ex, pd, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
      HttpMediaTypeNotAcceptableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail pd =
        Problems.build(
            status,
            "NOT_ACCEPTABLE",
            "Formato de resposta não suportado. Use Accept: application/json.",
            servletRequest(request));
    return handleExceptionInternal(ex, pd, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail pd =
        Problems.build(
            status, "NOT_FOUND", "O recurso solicitado não existe.", servletRequest(request));
    return handleExceptionInternal(ex, pd, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleNoHandlerFoundException(
      NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail pd =
        Problems.build(
            status, "NOT_FOUND", "O recurso solicitado não existe.", servletRequest(request));
    return handleExceptionInternal(ex, pd, headers, status, request);
  }

  /**
   * Last hop for every framework exception. Bodies already built by the overrides above carry a
   * "code" property; anything else (e.g. 413, 503) gets a generic pt-BR problem for its status.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail pd;
    Map<String, Object> props = body instanceof ProblemDetail p ? p.getProperties() : null;
    if (body instanceof ProblemDetail existing && props != null && props.containsKey("code")) {
      pd = existing;
    } else {
      pd =
          Problems.build(
              status, defaultCode(status), defaultDetail(status), servletRequest(request));
    }
    if (status.is5xxServerError()) {
      log.error("framework_error status={} type={}", status.value(), ex.getClass().getName(), ex);
    }
    HttpHeaders out = new HttpHeaders();
    if (headers != null) {
      out.putAll(headers);
    }
    out.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    return super.handleExceptionInternal(ex, pd, out, status, request);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static ResponseEntity<ProblemDetail> problem(HttpStatusCode status, ProblemDetail pd) {
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
  }

  private static ProblemDetail validationProblem(
      List<FieldProblem> errors, HttpServletRequest req) {
    ProblemDetail pd =
        Problems.build(
            HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Um ou mais campos são inválidos.", req);
    pd.setProperty("errors", errors);
    return pd;
  }

  private static HttpServletRequest servletRequest(WebRequest request) {
    return request instanceof ServletWebRequest swr ? swr.getRequest() : null;
  }

  private static String jsonPath(List<JsonMappingException.Reference> path) {
    return path.stream()
        .map(r -> r.getFieldName() != null ? r.getFieldName() : "[" + r.getIndex() + "]")
        .collect(Collectors.joining("."));
  }

  /** Java property path (e.g. serviceCode) to the snake_case name used on the wire. */
  static String jsonName(String propertyPath) {
    return java.util.Arrays.stream(propertyPath.split("\\."))
        .map(SNAKE_CASE::translate)
        .collect(Collectors.joining("."));
  }

  private static String lastNode(String propertyPath) {
    int dot = propertyPath.lastIndexOf('.');
    return dot >= 0 ? propertyPath.substring(dot + 1) : propertyPath;
  }

  static String defaultCode(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> "BAD_REQUEST";
      case 401 -> "AUTH_REQUIRED";
      case 403 -> "ACCESS_DENIED";
      case 404 -> "NOT_FOUND";
      case 405 -> "METHOD_NOT_ALLOWED";
      case 406 -> "NOT_ACCEPTABLE";
      case 409 -> "CONFLICT";
      case 413 -> "PAYLOAD_TOO_LARGE";
      case 415 -> "UNSUPPORTED_MEDIA_TYPE";
      case 429 -> "RATE_LIMITED";
      case 503 -> "SERVICE_UNAVAILABLE";
      default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "HTTP_" + status.value();
    };
  }

  static String defaultDetail(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> "Requisição inválida.";
      case 404 -> "O recurso solicitado não existe.";
      case 405 -> "Método não suportado por este recurso.";
      case 413 -> "O corpo da requisição excede o limite permitido.";
      case 415 -> "Content-Type não suportado. Envie o corpo como application/json.";
      case 503 -> "Serviço temporariamente indisponível. Tente novamente.";
      default ->
          status.is5xxServerError()
              ? "Erro inesperado. Tente novamente."
              : Problems.defaultTitle(status) + ".";
    };
  }
}
