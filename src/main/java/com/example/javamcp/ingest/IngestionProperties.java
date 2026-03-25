package com.example.javamcp.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp.ingest")
public record IngestionProperties(String resourcePattern, Boolean rebuildOnStartup) {

    public IngestionProperties {
        if (resourcePattern == null || resourcePattern.isBlank()) {
            resourcePattern = "classpath:/content/docs/*.json";
        }
        if (rebuildOnStartup == null) {
            rebuildOnStartup = Boolean.TRUE;
        }
    }
}
