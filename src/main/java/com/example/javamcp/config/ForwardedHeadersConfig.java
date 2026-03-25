package com.example.javamcp.config;

import org.apache.catalina.valves.RemoteIpValve;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ForwardedHeadersConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> trustedProxyForwardedHeaderCustomizer(
            McpIngressProperties ingressProperties) {
        return factory -> {
            RemoteIpValve valve = new RemoteIpValve();
            valve.setProtocolHeader("x-forwarded-proto");
            valve.setProtocolHeaderHttpsValue("https");
            valve.setRemoteIpHeader("x-forwarded-for");
            valve.setHostHeader("x-forwarded-host");
            valve.setPortHeader("x-forwarded-port");
            valve.setInternalProxies(ingressProperties.getTrustedProxies());
            factory.addEngineValves(valve);
        };
    }
}
