package com.example.javamcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "mcp.ingress")
@Validated
public class McpIngressProperties {

    private boolean enforceHttps = false;
    private boolean hstsEnabled = false;
    @Min(0)
    private long hstsMaxAgeSeconds = 31_536_000L;
    private boolean hstsIncludeSubDomains = true;
    private boolean hstsPreload = true;
    @NotBlank
    private String trustedProxies = "127\\.0\\.0\\.1|::1";

    public boolean isEnforceHttps() {
        return enforceHttps;
    }

    public void setEnforceHttps(boolean enforceHttps) {
        this.enforceHttps = enforceHttps;
    }

    public boolean isHstsEnabled() {
        return hstsEnabled;
    }

    public void setHstsEnabled(boolean hstsEnabled) {
        this.hstsEnabled = hstsEnabled;
    }

    public long getHstsMaxAgeSeconds() {
        return hstsMaxAgeSeconds;
    }

    public void setHstsMaxAgeSeconds(long hstsMaxAgeSeconds) {
        this.hstsMaxAgeSeconds = Math.max(0, hstsMaxAgeSeconds);
    }

    public boolean isHstsIncludeSubDomains() {
        return hstsIncludeSubDomains;
    }

    public void setHstsIncludeSubDomains(boolean hstsIncludeSubDomains) {
        this.hstsIncludeSubDomains = hstsIncludeSubDomains;
    }

    public boolean isHstsPreload() {
        return hstsPreload;
    }

    public void setHstsPreload(boolean hstsPreload) {
        this.hstsPreload = hstsPreload;
    }

    public String getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(String trustedProxies) {
        if (trustedProxies == null || trustedProxies.isBlank()) {
            this.trustedProxies = "127\\.0\\.0\\.1|::1";
            return;
        }
        this.trustedProxies = trustedProxies.trim();
    }
}
