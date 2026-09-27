// app_users repository. Parameterized SQL only; e-mail comparisons are case-insensitive
// (matching the unique index on lower(email)). Password hashes never leave this layer
// except through AppUser, which is not serialized to clients.
// Repositorio de usuarios: SQL parametrizado, e-mail sem diferenciar maiusculas.
package com.fwdford.forwardapi.repository;

import com.fwdford.forwardapi.model.AppUser;
import com.fwdford.forwardapi.security.Role;
import com.fwdford.forwardapi.security.UserSecurityState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

  private static final String SELECT =
      """
      SELECT u.id::text AS id, u.email, u.password_hash, u.full_name, u.role,
             u.dealer_id::text AS dealer_id, d.name AS dealer_name, u.active,
             u.last_login_at, u.created_at, u.updated_at, u.token_version
      FROM app_users u
      LEFT JOIN dealers d ON d.id = u.dealer_id
      """;

  private static final String FILTER =
      """
      WHERE (CAST(:role AS text) IS NULL OR u.role = CAST(:role AS text))
        AND (CAST(:active AS boolean) IS NULL OR u.active = CAST(:active AS boolean))
        AND (CAST(:dealerId AS uuid) IS NULL OR u.dealer_id = CAST(:dealerId AS uuid))
      """;

  private static final String INSERT =
      """
      INSERT INTO app_users (email, password_hash, full_name, role, dealer_id, active)
      VALUES (:email, :passwordHash, :fullName, :role, :dealerId, :active)
      RETURNING id::text AS id
      """;

  private static final String UPDATE =
      """
      UPDATE app_users
         SET full_name = :fullName,
             role = :role,
             dealer_id = :dealerId,
             active = :active,
             password_hash = COALESCE(:passwordHash, password_hash),
             token_version = token_version + CASE WHEN :revokeTokens THEN 1 ELSE 0 END
       WHERE id = :id
      """;

  private final NamedParameterJdbcTemplate jdbc;

  public UserRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<AppUser> findByEmail(String email) {
    return jdbc
        .query(
            SELECT + " WHERE lower(u.email) = lower(:email)",
            new MapSqlParameterSource("email", email),
            UserRepository::map)
        .stream()
        .findFirst();
  }

  public Optional<AppUser> findById(UUID id) {
    return jdbc
        .query(
            SELECT + " WHERE u.id = :id", new MapSqlParameterSource("id", id), UserRepository::map)
        .stream()
        .findFirst();
  }

  public List<AppUser> list(Role role, Boolean active, UUID dealerId, int limit, int offset) {
    MapSqlParameterSource params = filterParams(role, active, dealerId);
    params.addValue("lim", limit).addValue("off", offset);
    return jdbc.query(
        SELECT + FILTER + " ORDER BY u.created_at ASC, u.email ASC LIMIT :lim OFFSET :off",
        params,
        UserRepository::map);
  }

  public long count(Role role, Boolean active, UUID dealerId) {
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM app_users u " + FILTER,
            filterParams(role, active, dealerId),
            Long.class);
    return total == null ? 0 : total;
  }

  public boolean existsByEmail(String email) {
    Boolean exists =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM app_users WHERE lower(email) = lower(:email))",
            new MapSqlParameterSource("email", email),
            Boolean.class);
    return Boolean.TRUE.equals(exists);
  }

  public UUID insert(
      String email,
      String passwordHash,
      String fullName,
      Role role,
      UUID dealerId,
      boolean active) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("email", email)
            .addValue("passwordHash", passwordHash)
            .addValue("fullName", fullName)
            .addValue("role", role.name())
            .addValue("dealerId", dealerId, Types.OTHER)
            .addValue("active", active);
    String id = jdbc.queryForObject(INSERT, params, String.class);
    if (id == null) {
      throw new IllegalStateException("INSERT app_users returned no id");
    }
    return UUID.fromString(id);
  }

  /**
   * Updates the user. revokeTokens increments token_version, which invalidates every JWT issued
   * before this change.
   */
  public int update(
      UUID id,
      String fullName,
      Role role,
      UUID dealerId,
      boolean active,
      String newPasswordHash,
      boolean revokeTokens) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("fullName", fullName)
            .addValue("role", role.name())
            .addValue("dealerId", dealerId, Types.OTHER)
            .addValue("active", active)
            .addValue("passwordHash", newPasswordHash, Types.VARCHAR)
            .addValue("revokeTokens", revokeTokens);
    return jdbc.update(UPDATE, params);
  }

  public int deleteById(UUID id) {
    return jdbc.update("DELETE FROM app_users WHERE id = :id", new MapSqlParameterSource("id", id));
  }

  public void touchLastLogin(UUID id) {
    jdbc.update(
        "UPDATE app_users SET last_login_at = NOW() WHERE id = :id",
        new MapSqlParameterSource("id", id));
  }

  private static MapSqlParameterSource filterParams(Role role, Boolean active, UUID dealerId) {
    return new MapSqlParameterSource()
        .addValue("role", role == null ? null : role.name(), Types.VARCHAR)
        .addValue("active", active, Types.BOOLEAN)
        .addValue("dealerId", dealerId, Types.OTHER);
  }

  private static AppUser map(ResultSet rs, int rowNum) throws SQLException {
    return new AppUser(
        rs.getString("id"),
        rs.getString("email"),
        rs.getString("password_hash"),
        rs.getString("full_name"),
        Role.valueOf(rs.getString("role")),
        rs.getString("dealer_id"),
        rs.getString("dealer_name"),
        rs.getBoolean("active"),
        rs.getObject("last_login_at", OffsetDateTime.class),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class),
        rs.getLong("token_version"));
  }

  /** Security-relevant state used to revoke tokens; empty when the user does not exist. */
  public Optional<UserSecurityState> findSecurityState(UUID id) {
    return jdbc
        .query(
            "SELECT active, role, dealer_id::text AS dealer_id, token_version"
                + " FROM app_users WHERE id = :id",
            new MapSqlParameterSource("id", id),
            (rs, i) ->
                new UserSecurityState(
                    rs.getBoolean("active"),
                    Role.valueOf(rs.getString("role")),
                    rs.getString("dealer_id"),
                    rs.getLong("token_version")))
        .stream()
        .findFirst();
  }
}
