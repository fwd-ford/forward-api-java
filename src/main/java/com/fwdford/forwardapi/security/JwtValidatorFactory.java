// Picks the best JWT validator for the current configuration.
// Priority: JWKS URL > HS256 secret > none (development-only bypass).
// Escolhe o validador JWT; JWKS > HS256 > bypass (dev).
package com.fwdford.forwardapi.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fwdford.forwardapi.config.AppProperties;

@Configuration
public class JwtValidatorFactory {

    private static final Logger log = LoggerFactory.getLogger(JwtValidatorFactory.class);

    @Bean
    public JwtValidator jwtValidator(AppProperties props) throws Exception {
        var jwt = props.jwt();
        if (jwt != null && jwt.jwksUrl() != null && !jwt.jwksUrl().isBlank()) {
            log.info("jwt validator: JWKS (asymmetric) url={}", jwt.jwksUrl());
            return new JwksJwtValidator(jwt.jwksUrl());
        }
        if (jwt != null && jwt.secret() != null && !jwt.secret().isBlank()) {
            log.info("jwt validator: HS256 (shared secret)");
            return new Hs256JwtValidator(jwt.secret());
        }
        log.warn("jwt validator: DISABLED (no SUPABASE_JWT_SECRET or SUPABASE_JWKS_URL). Endpoints are open.");
        return null;
    }
}
