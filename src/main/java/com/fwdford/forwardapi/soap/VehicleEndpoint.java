// SOAP endpoint for the vehicles service. Single operation: GetVehicle (contract-first,
// xsd/vehicles.xsd). Requires a valid Bearer JWT (enforced by the Spring Security chain,
// which also covers the /soap/* servlet) and applies the same dealer scoping as the REST
// GET /api/v1/vehicles/{vin}. Builds the response DOM by hand to avoid JAXB generation.
// Endpoint SOAP de veiculos (GetVehicle): exige JWT e aplica o mesmo escopo do REST.
package com.fwdford.forwardapi.soap;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.Vehicle;
import com.fwdford.forwardapi.security.CurrentUser;
import com.fwdford.forwardapi.service.VehicleService;
import com.fwdford.forwardapi.web.Validations;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;
import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Endpoint
public class VehicleEndpoint {

  private static final String NS = "urn:forwardservice:vehicles";

  private final VehicleService service;

  public VehicleEndpoint(VehicleService service) {
    this.service = service;
  }

  // Spring WS maps org.w3c.dom.Element both as request and response payload
  // (DomPayloadMethodProcessor). A DOMResult return type has no adapter and failed.
  // Element e suportado como entrada e saida; DOMResult nao tinha adaptador.
  @PayloadRoot(namespace = NS, localPart = "GetVehicleRequest")
  @ResponsePayload
  public Element getVehicle(@RequestPayload Element request) throws ParserConfigurationException {
    String rawVin = extractText(request, "VIN");
    Vehicle v;
    try {
      String vin = Validations.validateVin(rawVin);
      v = service.get(vin, CurrentUser.require());
    } catch (ApiException ex) {
      throw new SoapClientFault(ex.detail());
    }

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Document doc = factory.newDocumentBuilder().newDocument();
    Element response = doc.createElementNS(NS, "GetVehicleResponse");
    doc.appendChild(response);
    appendChild(doc, response, "VIN", v.vin());
    appendChild(doc, response, "Model", v.model());
    appendChild(doc, response, "Year", Integer.toString(v.year()));
    appendChild(doc, response, "Discontinued", Boolean.toString(v.discontinued()));
    return response;
  }

  private static String extractText(Element parent, String name) {
    NodeList nodes = parent.getElementsByTagNameNS("*", name);
    if (nodes.getLength() == 0) {
      throw new SoapClientFault("Elemento " + name + " ausente no envelope SOAP.");
    }
    return nodes.item(0).getTextContent();
  }

  private static void appendChild(Document doc, Element parent, String name, String value) {
    Element el = doc.createElementNS(NS, name);
    el.setTextContent(value);
    parent.appendChild(el);
  }

  // SOAP Fault with faultcode=Client when the caller submits an invalid payload (bad VIN,
  // missing element, unknown vehicle, other dealer). The fault string is the pt-BR detail.
  // SOAP Fault (Client) com a mensagem em portugues como faultstring.
  @SoapFault(faultCode = FaultCode.CLIENT, locale = "pt")
  public static class SoapClientFault extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SoapClientFault(String message) {
      super(message);
    }
  }
}
