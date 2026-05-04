// Attaches a correlation ID to every request. Accepts an incoming X-Request-Id
// header or generates a fresh UUID. Also pushes the id into the SLF4J MDC.
// Anexa um ID de correlacao a cada request e coloca no MDC para logs.
package com.fwdford.forwardapi.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(0)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String rid = req.getHeader(HEADER);
        if (rid == null || rid.isBlank()) {
            rid = UUID.randomUUID().toString();
        }
        req.setAttribute(WebAttrs.REQUEST_ID, rid);
        resp.setHeader(HEADER, rid);
        MDC.put("request_id", rid);
        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.remove("request_id");
        }
    }
}
