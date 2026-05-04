// Structured API exception carrying everything needed to render an RFC 7807 Problem.
// Excecao estruturada que carrega dados para renderizar um Problem RFC 7807.
package com.fwdford.forwardapi.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String title;
    private final String detail;

    public ApiException(HttpStatus status, String code, String title, String detail) {
        super(title + ": " + detail);
        this.status = status;
        this.code = code;
        this.title = title;
        this.detail = detail;
    }

    public HttpStatus status() { return status; }
    public String code()       { return code; }
    public String title()      { return title; }
    public String detail()     { return detail; }

    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Requisicao invalida", detail);
    }
    public static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Nao autenticado", "Token ausente ou invalido.");
    }
    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Acesso negado", "Sem permissao para este recurso.");
    }
    public static ApiException notFound(String resource) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", "Recurso nao encontrado",
                "O recurso " + resource + " nao foi encontrado.");
    }
    public static ApiException tooManyRequests() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", "Muitas requisicoes",
                "Tente novamente em instantes.");
    }
    public static ApiException payloadTooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "payload_too_large",
                "Corpo da requisicao muito grande", "O conteudo excede o limite permitido.");
    }
    public static ApiException internal() {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Erro interno",
                "Tente novamente em instantes.");
    }
}
