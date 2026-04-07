package com.example.javamcp.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "mcp.health")
@Validated
public class McpHealthProperties {

    @NotNull
    private Duration maxIndexAge = Duration.ofHours(24);

    public Duration getMaxIndexAge() {
        return maxIndexAge;
    }

    public void setMaxIndexAge(Duration maxIndexAge) {
        this.maxIndexAge = maxIndexAge == null || maxIndexAge.isNegative() || maxIndexAge.isZero()
                ? Duration.ofHours(24)
                : maxIndexAge;
    }
}
