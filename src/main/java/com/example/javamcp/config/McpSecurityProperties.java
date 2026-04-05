package com.example.javamcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "mcp.security")
@Validated
public class McpSecurityProperties {

    private String apiKey = "";
    @NotBlank
    private String headerName = "X-API-Key";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        if (headerName == null || headerName.isBlank()) {
            this.headerName = "X-API-Key";
            return;
        }
        this.headerName = headerName.trim();
    }

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
