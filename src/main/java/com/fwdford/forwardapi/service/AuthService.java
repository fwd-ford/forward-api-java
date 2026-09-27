// Login: verifies e-mail + password against app_users (BCrypt) and issues a JWT.
// Unknown e-mail and wrong password produce the SAME 401 and take the same time (a dummy
// BCrypt verification runs when the e-mail does not exist), so the endpoint does not
// reveal which accounts exist. "User disabled" is only reported after the password
// matched. Every attempt is written to audit_log.
// Login: valida credenciais (BCrypt), emite JWT e nao revela se o e-mail existe.
package com.fwdford.forwardapi.service;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.AppUser;
import com.fwdford.forwardapi.repository.UserRepository;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.security.JwtService;
import com.fwdford.forwardapi.web.dto.LoginResponse;
import com.fwdford.forwardapi.web.dto.UserSummary;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final JwtService jwtService;
  private final AuditService audit;
  private final String dummyHash;

  public AuthService(
      UserRepository users, PasswordEncoder encoder, JwtService jwtService, AuditService audit) {
    this.users = users;
    this.encoder = encoder;
    this.jwtService = jwtService;
    this.audit = audit;
    // Hash of a random value: used to spend the same BCrypt time for unknown e-mails.
    this.dummyHash = encoder.encode("timing-equalizer-" + UUID.randomUUID());
  }

  public LoginResponse login(String email, String password) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    Optional<AppUser> found = users.findByEmail(normalized);

    if (found.isEmpty()) {
      encoder.matches(password, dummyHash);
      audit.record(
          null, "auth.login_failed", "auth", null, Map.of("reason", "invalid_credentials"));
      log.info("login_failed reason=invalid_credentials");
      throw ApiException.invalidCredentials();
    }

    AppUser user = found.get();
    if (!encoder.matches(password, user.passwordHash())) {
      audit.record(
          null,
          "auth.login_failed",
          "app_user",
          user.id(),
          Map.of("reason", "invalid_credentials"));
      log.info("login_failed reason=invalid_credentials user_id={}", user.id());
      throw ApiException.invalidCredentials();
    }
    if (!user.active()) {
      audit.record(null, "auth.login_failed", "app_user", user.id(), Map.of("reason", "disabled"));
      log.info("login_failed reason=user_disabled user_id={}", user.id());
      throw ApiException.userDisabled();
    }

    AuthenticatedUser principal =
        new AuthenticatedUser(
            user.id(),
            user.email(),
            user.fullName(),
            user.role(),
            user.dealerId(),
            user.tokenVersion());
    JwtService.IssuedToken token = jwtService.issue(principal);
    users.touchLastLogin(UUID.fromString(user.id()));
    audit.record(
        principal, "auth.login_succeeded", "app_user", user.id(), Map.of("jti", token.jti()));
    log.info("login_succeeded user_id={} role={}", user.id(), user.role());

    return new LoginResponse(
        token.token(),
        "Bearer",
        token.expiresInSeconds(),
        token.expiresAt(),
        UserSummary.from(user));
  }
}
