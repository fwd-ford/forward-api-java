// Spring Web Services bootstrap. Exposes the MessageDispatcherServlet under /soap/*
// and publishes a dynamically generated WSDL at /soap/vehicles.wsdl.
// Bootstrap do Spring WS: expoe /soap/* e publica WSDL dinamico em /soap/vehicles.wsdl.
package com.fwdford.forwardapi.soap;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

@EnableWs
@Component
public class WebServiceConfig extends WsConfigurerAdapter {

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext ctx) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(ctx);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/soap/*");
    }

    // Bean name "vehicles" drives the WSDL URL: /soap/vehicles.wsdl.
    // O nome do bean "vehicles" define a URL do WSDL: /soap/vehicles.wsdl.
    @Bean(name = "vehicles")
    public DefaultWsdl11Definition vehiclesWsdl(XsdSchema vehiclesSchema) {
        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("VehiclesPortType");
        wsdl.setLocationUri("/soap/vehicles");
        wsdl.setTargetNamespace("urn:forwardservice:vehicles");
        wsdl.setSchema(vehiclesSchema);
        return wsdl;
    }

    @Bean
    public XsdSchema vehiclesSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/vehicles.xsd"));
    }
}
