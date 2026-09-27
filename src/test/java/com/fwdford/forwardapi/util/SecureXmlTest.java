package com.fwdford.forwardapi.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/** XXE hardening of the DOM factory used by the SOAP endpoint. */
class SecureXmlTest {

  private static Document parse(String xml) throws Exception {
    return SecureXml.documentBuilderFactory()
        .newDocumentBuilder()
        .parse(new InputSource(new StringReader(xml)));
  }

  @Test
  void doctype_with_external_entity_is_rejected() {
    String xxe =
        "<?xml version=\"1.0\"?>"
            + "<!DOCTYPE v [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
            + "<VIN>&xxe;</VIN>";
    assertThrows(SAXParseException.class, () -> parse(xxe));
  }

  @Test
  void internal_entity_expansion_bomb_is_rejected() {
    String bomb =
        "<?xml version=\"1.0\"?>"
            + "<!DOCTYPE lolz [<!ENTITY lol \"lol\"><!ENTITY lol2 \"&lol;&lol;&lol;&lol;\">]>"
            + "<VIN>&lol2;</VIN>";
    assertThrows(SAXParseException.class, () -> parse(bomb));
  }

  @Test
  void plain_document_without_doctype_is_parsed() throws Exception {
    Document doc = parse("<GetVehicleRequest><VIN>9BFZZZ5SZJB000001</VIN></GetVehicleRequest>");
    assertEquals("9BFZZZ5SZJB000001", doc.getDocumentElement().getTextContent());
  }

  @Test
  void factory_disables_xinclude_and_entity_expansion() throws Exception {
    DocumentBuilderFactory factory = SecureXml.documentBuilderFactory();
    assertFalse(factory.isXIncludeAware());
    assertFalse(factory.isExpandEntityReferences());
  }
}
