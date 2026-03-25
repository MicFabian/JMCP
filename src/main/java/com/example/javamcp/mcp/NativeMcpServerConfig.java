package com.example.javamcp.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class NativeMcpServerConfig {

    @Bean
    public HttpServletStreamableServerTransportProvider mcpStreamableTransportProvider() {
        return HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint("/mcp")
                .keepAliveInterval(Duration.ofSeconds(20))
                .build();
    }

    @Bean(destroyMethod = "closeGracefully")
    public McpSyncServer nativeMcpSyncServer(HttpServletStreamableServerTransportProvider transportProvider,
                                             NativeMcpSpecificationFactory specificationFactory) {
        McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
                .tools(true)
                .resources(false, true)
                .prompts(true)
                .build();

        return McpServer.sync(transportProvider)
                .serverInfo("java-mcp", "v0.1.0")
                .instructions("""
                        Java MCP server for search, Java analysis, and Spring/Java documentation retrieval.
                        Prefer 'resolve-library-id' + 'query-docs' for framework guidance and cite source URLs.
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
            HttpServletStreamableServerTransportProvider transportProvider,
            McpSyncServer ignoredMcpServerBean) {
        ServletRegistrationBean<HttpServlet> registration =
                new ServletRegistrationBean<>(transportProvider, "/mcp", "/mcp/*");
        registration.setName("mcpStreamableHttpServlet");
        registration.setLoadOnStartup(1);
        registration.setAsyncSupported(true);
        return registration;
    }
}
