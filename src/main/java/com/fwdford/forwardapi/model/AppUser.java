// Application user as stored in app_users (internal model, never serialized directly:
// it carries the BCrypt hash). API responses use UserSummary / UserResponse.
// Usuario da aplicacao (modelo interno; nunca serializado porque contem o hash).
package com.fwdford.forwardapi.model;

import com.fwdford.forwardapi.security.Role;
import java.time.OffsetDateTime;

public record AppUser(
    String id,
    String email,
    String passwordHash,
    String fullName,
    Role role,
    String dealerId,
    String dealerName,
    boolean active,
    OffsetDateTime lastLoginAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  // Keeps the password hash out of logs and exception messages.
  @Override
  public String toString() {
    return "AppUser[id=" + id + ", email=" + email + ", role=" + role + ", active=" + active + "]";
  }
}
