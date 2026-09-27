// Appends security-relevant events to audit_log (append-only table, migration 009).
// Captures actor, action, resource, client IP, user agent and request id; the payload
// is stored as JSONB. Runs inside the caller's transaction so business changes and
// their audit entry commit (or roll back) together.
// Grava eventos de auditoria no audit_log junto com a transacao do chamador.
package com.fwdford.forwardapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.web.ClientIpResolver;
import com.fwdford.forwardapi.web.WebAttrs;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Types;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

  private static final String INSERT =
      """
      INSERT INTO audit_log
        (actor_id, actor_role, action, resource_type, resource_id, ip_address, user_agent,
         request_id, payload)
      VALUES
        (:actorId, :actorRole, :action, :resourceType, :resourceId, CAST(:ip AS inet), :userAgent,
         :requestId, CAST(:payload AS jsonb))
      """;

  private static final Pattern UUID_RE =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
  private static final int MAX_USER_AGENT = 256;

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final ClientIpResolver ipResolver;

  public AuditService(
      NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, ClientIpResolver ipResolver) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.ipResolver = ipResolver;
  }

  public void record(
      AuthenticatedUser actor,
      String action,
      String resourceType,
      String resourceId,
      Map<String, ?> payload) {
    HttpServletRequest req = currentRequest();
    String ip = req != null ? ClientIpResolver.asInetLiteral(ipResolver.resolve(req)) : null;
    String userAgent = req != null ? truncate(req.getHeader("User-Agent")) : null;
    Object requestId = req != null ? req.getAttribute(WebAttrs.REQUEST_ID) : null;
    UUID actorId =
        actor != null && actor.id() != null && UUID_RE.matcher(actor.id()).matches()
            ? UUID.fromString(actor.id())
            : null;

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("actorId", actorId, Types.OTHER)
            .addValue("actorRole", actor != null ? actor.role().name() : null, Types.VARCHAR)
            .addValue("action", action)
            .addValue("resourceType", resourceType)
            .addValue("resourceId", resourceId, Types.VARCHAR)
            .addValue("ip", ip, Types.VARCHAR)
            .addValue("userAgent", userAgent, Types.VARCHAR)
            .addValue("requestId", requestId instanceof String s ? s : null, Types.VARCHAR)
            .addValue("payload", toJson(payload), Types.VARCHAR);
    jdbc.update(INSERT, params);
  }

  private String toJson(Map<String, ?> payload) {
    if (payload == null || payload.isEmpty()) {
      return null;
    }
    try {
      return mapper.writeValueAsString(payload);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("audit payload is not serializable", ex);
    }
  }

  private static HttpServletRequest currentRequest() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      return attrs.getRequest();
    }
    return null;
  }

  private static String truncate(String value) {
    if (value == null) {
      return null;
    }
    return value.length() <= MAX_USER_AGENT ? value : value.substring(0, MAX_USER_AGENT);
  }
}
