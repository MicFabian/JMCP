package com.example.javamcp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mcpOpenApi(McpSecurityProperties securityProperties) {
        OpenAPI openApi = new OpenAPI()
                .info(new Info()
                        .title("Java MCP API")
                        .version("v0.1.0")
                        .description("Machine-Consumable Knowledge Platform API for search, AST analysis, rule checks, and symbol graph extraction")
                        .contact(new Contact().name("MCP Engineering"))
                        .license(new License().name("Apache-2.0")))
                .components(new Components().addSecuritySchemes(
                        "ApiKeyAuth",
                        new SecurityScheme()
                                .name(securityProperties.getHeaderName())
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Optional API key auth. Enabled when mcp.security.api-key is configured.")
                ));

        if (securityProperties.enabled()) {
            openApi.addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"));
        }

        return openApi;
    }
}
