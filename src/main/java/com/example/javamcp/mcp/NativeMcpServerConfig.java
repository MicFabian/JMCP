package com.example.javamcp.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class NativeMcpServerConfig {

    @Bean
    public HttpServletStatelessServerTransport mcpStatelessTransport() {
        return HttpServletStatelessServerTransport.builder()
                .messageEndpoint("/mcp")
                .build();
    }

    @Bean(destroyMethod = "closeGracefully")
    public McpStatelessSyncServer nativeMcpSyncServer(HttpServletStatelessServerTransport transport,
                                                      NativeMcpSpecificationFactory specificationFactory) {
        McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
                .tools(true)
                .resources(false, true)
                .prompts(true)
                .build();

        return McpServer.sync(transport)
                .serverInfo("java-mcp", "v0.1.0")
                .instructions("""
                        Java MCP server for Spring, Java, Groovy, Snappo, and OpenJDK documentation plus Java code analysis.
                        Use 'java-docs' for framework usage, configuration, Groovy or Spock testing guidance, Snappo snapshot-testing guidance, API, migration, and best-practice questions.
                        Use 'analyze' before suggesting fixes for Java code snippets, 'symbols' for call-graph or dependency questions,
                        'migration-assistant' for Java 25 or Spring Boot 4 upgrade planning, and prefer Groovy with Snappo when advising on JVM snapshot tests.
                        Cite sourceUrl values from tool results when answering.
                        """)
                .capabilities(capabilities)
                .requestTimeout(Duration.ofSeconds(30))
                .tools(specificationFactory.toolSpecifications())
                .resources(specificationFactory.resourceSpecifications())
                .resourceTemplates(specificationFactory.resourceTemplateSpecifications())
                .prompts(specificationFactory.promptSpecifications())
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServlet> mcpServletRegistration(
            HttpServletStatelessServerTransport transport,
            McpStatelessSyncServer ignoredMcpServerBean) {
        ServletRegistrationBean<HttpServlet> registration =
                new ServletRegistrationBean<>(transport, "/mcp", "/mcp/*");
        registration.setName("mcpStreamableHttpServlet");
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);
        return registration;
    }
}
