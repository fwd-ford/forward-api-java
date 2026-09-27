// Structured API exception carrying everything needed to render an RFC 7807 Problem.
// Codes are stable UPPER_SNAKE identifiers clients can switch on; detail is pt-BR.
// Excecao estruturada: codigo estavel para o cliente e detalhe em portugues.
package com.fwdford.forwardapi.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final HttpStatus status;
  private final String code;
  private final String title;
  private final String detail;

  public ApiException(HttpStatus status, String code, String detail) {
    this(status, code, Problems.defaultTitle(status), detail);
  }

  public ApiException(HttpStatus status, String code, String title, String detail) {
    super(code + ": " + detail);
    this.status = status;
    this.code = code;
    this.title = title;
    this.detail = detail;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public String title() {
    return title;
  }

  public String detail() {
    return detail;
  }

  public static ApiException badRequest(String detail) {
    return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", detail);
  }

  public static ApiException badRequest(String code, String detail) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
  }

  public static ApiException invalidCredentials() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "E-mail ou senha inválidos.");
  }

  public static ApiException userDisabled() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED,
        "AUTH_USER_DISABLED",
        "Usuário desativado. Procure um administrador.");
  }

  public static ApiException unauthorized() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Autenticação necessária para este recurso.");
  }

  public static ApiException forbidden() {
    return new ApiException(
        HttpStatus.FORBIDDEN,
        "ACCESS_DENIED",
        "Seu perfil não tem permissão para executar esta operação.");
  }

  public static ApiException forbiddenOtherDealer() {
    return new ApiException(
        HttpStatus.FORBIDDEN,
        "ACCESS_OTHER_DEALER",
        "Este recurso pertence a outra concessionária.");
  }

  public static ApiException notFound(String code, String detail) {
    return new ApiException(HttpStatus.NOT_FOUND, code, detail);
  }

  public static ApiException conflict(String code, String detail) {
    return new ApiException(HttpStatus.CONFLICT, code, detail);
  }

  public static ApiException unprocessable(String code, String detail) {
    return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, detail);
  }

  public static ApiException tooManyRequests() {
    return new ApiException(
        HttpStatus.TOO_MANY_REQUESTS,
        "RATE_LIMITED",
        "Limite de requisições excedido. Tente novamente em instantes.");
  }

  public static ApiException payloadTooLarge() {
    return new ApiException(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "PAYLOAD_TOO_LARGE",
        "O corpo da requisição excede o limite permitido.");
  }

  public static ApiException internal() {
    return new ApiException(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erro inesperado. Tente novamente.");
  }
}
