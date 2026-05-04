// Liveness and readiness endpoints. Public: do not touch dependencies.
// Endpoints de saude: publicos e nao testam dependencias.
package com.fwdford.forwardapi.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final String version;

    public HealthController(@Value("${forward.version:dev}") String version) {
        this.version = version;
    }

    @GetMapping({"/health", "/ready"})
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "timestamp", Instant.now(),
                "service", "forward-api",
                "version", version
        );
    }
}
