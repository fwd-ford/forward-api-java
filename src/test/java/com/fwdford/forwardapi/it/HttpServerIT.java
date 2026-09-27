package com.fwdford.forwardapi.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fwdford.forwardapi.security.AuthenticatedUser;
import com.fwdford.forwardapi.security.JwtService;
import com.fwdford.forwardapi.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs the real embedded Tomcat on a random port: covers what MockMvc cannot reach, namely the SOAP
 * servlet (/soap/*) and the WSDL, plus the public endpoints over real HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HttpServerIT {

  private static final String SOAP_REQUEST =
      """
      <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                        xmlns:v="urn:forwardservice:vehicles">
        <soapenv:Header/>
        <soapenv:Body>
          <v:GetVehicleRequest><v:VIN>%s</v:VIN></v:GetVehicleRequest>
        </soapenv:Body>
      </soapenv:Envelope>
      """;

  @Autowired private TestRestTemplate http;
  @Autowired private JwtService jwtService;

  private String adminToken() {
    return jwtService
        .issue(
            new AuthenticatedUser(
                TestData.ADMIN_ID, "admin@forward.dev", "Ana Paula Ribeiro", Role.ADMIN, null))
        .token();
  }

  private ResponseEntity<String> soap(String vin, String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.TEXT_XML);
    headers.add("SOAPAction", "\"\"");
    if (token != null) {
      headers.setBearerAuth(token);
    }
    return http.exchange(
        "/soap/vehicles",
        HttpMethod.POST,
        new HttpEntity<>(SOAP_REQUEST.formatted(vin), headers),
        String.class);
  }

  @Test
  @DisplayName("WSDL é público e descreve a operação GetVehicle")
  void wsdl_is_public() {
    ResponseEntity<String> wsdl = http.getForEntity("/soap/vehicles.wsdl", String.class);
    assertThat(wsdl.getStatusCode().value()).isEqualTo(200);
    assertThat(wsdl.getBody()).contains("GetVehicle").contains("urn:forwardservice:vehicles");
  }

  @Test
  @DisplayName("SOAP GetVehicle sem token: 401")
  void soap_requires_token() {
    assertThat(soap(TestData.VIN_DEALER_1, null).getStatusCode().value()).isEqualTo(401);
  }

  @Test
  @DisplayName("SOAP GetVehicle com token: 200 com os dados do veículo")
  void soap_with_token_returns_vehicle() {
    ResponseEntity<String> resp = soap(TestData.VIN_DEALER_1, adminToken());
    assertThat(resp.getStatusCode().value()).isEqualTo(200);
    assertThat(resp.getBody()).contains("GetVehicleResponse").contains("Ka").contains("2018");
  }

  @Test
  @DisplayName("SOAP GetVehicle com VIN inexistente: SOAP Fault (Client)")
  void soap_unknown_vin_is_fault() {
    ResponseEntity<String> resp = soap(TestData.VIN_UNKNOWN, adminToken());
    assertThat(resp.getStatusCode().value()).isEqualTo(500);
    assertThat(resp.getBody()).contains("Fault").contains("Veículo não encontrado");
  }

  @Test
  @DisplayName("health, OpenAPI e Swagger UI respondem sem token em HTTP real")
  void public_endpoints_over_http() {
    assertThat(http.getForEntity("/health", String.class).getStatusCode().value()).isEqualTo(200);
    assertThat(http.getForEntity("/v3/api-docs.yaml", String.class).getBody())
        .contains("ForwardService API");
    assertThat(http.getForEntity("/swagger-ui/index.html", String.class).getStatusCode().value())
        .isEqualTo(200);
  }

  @Test
  @DisplayName("rota protegida sem token em HTTP real: 401 problem+json")
  void protected_endpoint_over_http() {
    ResponseEntity<String> resp = http.getForEntity("/api/v1/leads", String.class);
    assertThat(resp.getStatusCode().value()).isEqualTo(401);
    assertThat(resp.getHeaders().getContentType()).isNotNull();
    assertThat(resp.getHeaders().getContentType().toString()).contains("application/problem+json");
    assertThat(resp.getBody()).contains("AUTH_REQUIRED");
  }
}
