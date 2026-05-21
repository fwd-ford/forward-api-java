// SOAP endpoint for the vehicles service. Single operation: GetVehicle.
// Produces typed XML by hand to avoid JAXB class generation overhead.
// Endpoint SOAP de veiculos: uma operacao (GetVehicle). XML tipado manualmente sem JAXB gen.
package com.fwdford.forwardapi.soap;

import com.fwdford.forwardapi.error.ApiException;
import com.fwdford.forwardapi.model.Vehicle;
import com.fwdford.forwardapi.service.VehicleService;
import com.fwdford.forwardapi.web.Validations;
import javax.xml.transform.dom.DOMResult;
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

  @PayloadRoot(namespace = NS, localPart = "GetVehicleRequest")
  @ResponsePayload
  public DOMResult getVehicle(@RequestPayload Element request) throws Exception {
    String rawVin = extractText(request, "VIN");
    String vin;
    try {
      vin = Validations.validateVin(rawVin);
    } catch (ApiException ex) {
      throw new SoapClientFault(ex.detail());
    }

    Vehicle v;
    try {
      v = service.get(vin);
    } catch (ApiException ex) {
      throw new SoapClientFault(ex.detail());
    }

    Document doc =
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    Element response = doc.createElementNS(NS, "GetVehicleResponse");
    doc.appendChild(response);
    appendChild(doc, response, "VIN", v.vin());
    appendChild(doc, response, "Model", v.model());
    appendChild(doc, response, "Year", Integer.toString(v.year()));
    appendChild(doc, response, "Discontinued", Boolean.toString(v.discontinued()));
    return new DOMResult(doc);
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

  // Sends back a SOAP Fault with faultcode=Client when the caller submits
  // an invalid payload (bad VIN, missing element, unknown vehicle).
  // Retorna um SOAP Fault com faultcode=Client quando o chamador manda payload invalido.
  @SoapFault(faultCode = FaultCode.CLIENT, faultStringOrReason = "Requisicao SOAP invalida")
  public static class SoapClientFault extends RuntimeException {
    public SoapClientFault(String message) {
      super(message);
    }
  }
}
