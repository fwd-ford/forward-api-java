// Shared request-attribute keys set by security filters and read by controllers.
// Chaves de atributos de request compartilhadas entre filtros e controllers.
package com.fwdford.forwardapi.web;

public final class WebAttrs {
    public static final String PRINCIPAL  = "forward.principal";
    public static final String REQUEST_ID = "forward.requestId";

    private WebAttrs() {}
}
