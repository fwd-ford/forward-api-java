package com.fwdford.forwardapi.it;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fwdford.forwardapi.config.AppProperties;
import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.security.JwtService;
import com.fwdford.forwardapi.security.Role;
import java.util.concurrent.atomic.AtomicInteger;
import org.hamcrest.Matchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Base class for HTTP-level tests: full Spring context (filters, Spring Security, MVC, JDBC)
 * against an embedded PostgreSQL 16 with the Flyway migrations and the demo seed. All subclasses
 * share one cached context, hence one database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

  private static final AtomicInteger IP_SEQ = new AtomicInteger(1);

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper mapper;
  @Autowired protected JwtService jwtService;
  @Autowired protected AppProperties props;
  @Autowired protected NamedParameterJdbcTemplate jdbc;

  protected static final AuthenticatedUser ADMIN =
      new AuthenticatedUser(
          TestData.ADMIN_ID, "admin@forward.dev", "Ana Paula Ribeiro", Role.ADMIN, null);
  protected static final AuthenticatedUser GESTOR =
      new AuthenticatedUser(
          TestData.GESTOR_ID,
          "gestor@forward.dev",
          "Gustavo Mendes",
          Role.GESTOR,
          TestData.DEALER_1);
  protected static final AuthenticatedUser ATENDENTE =
      new AuthenticatedUser(
          TestData.ATENDENTE_ID,
          "atendente@forward.dev",
          "Beatriz Santos",
          Role.ATENDENTE,
          TestData.DEALER_1);
  protected static final AuthenticatedUser ATENDENTE_2 =
      new AuthenticatedUser(
          TestData.ATENDENTE_2_ID,
          "atendente2@forward.dev",
          "Diego Carvalho",
          Role.ATENDENTE,
          TestData.DEALER_2);

  /** Authorization header value with a valid token for the given principal. */
  protected String bearer(AuthenticatedUser user) {
    return "Bearer " + jwtService.issue(user).token();
  }

  protected String json(Object body) throws Exception {
    return mapper.writeValueAsString(body);
  }

  /** Gives each call its own client IP so the login rate limit never leaks between tests. */
  protected static RequestPostProcessor uniqueIp() {
    int n = IP_SEQ.getAndIncrement();
    String ip = "10.20." + (n / 250) + "." + (n % 250 + 1);
    return request -> {
      request.setRemoteAddr(ip);
      return request;
    };
  }

  protected static RequestPostProcessor fromIp(String ip) {
    return request -> {
      request.setRemoteAddr(ip);
      return request;
    };
  }

  /** Asserts the RFC 7807 body shared by every error of the API. */
  protected static ResultMatcher problem(int status, String code) {
    return result -> {
      content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
      jsonPath("$.status").value(status).match(result);
      jsonPath("$.code").value(code).match(result);
      jsonPath("$.type")
          .value("urn:forward:problem:" + code.toLowerCase().replace('_', '-'))
          .match(result);
      jsonPath("$.title").isNotEmpty().match(result);
      jsonPath("$.detail").isNotEmpty().match(result);
      jsonPath("$.instance").isNotEmpty().match(result);
      jsonPath("$.timestamp").isNotEmpty().match(result);
      jsonPath("$.request_id").isNotEmpty().match(result);
      header().string("X-Request-Id", Matchers.notNullValue()).match(result);
    };
  }
}
