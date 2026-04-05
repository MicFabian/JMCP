package com.example.javamcp.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "mcp.ingest")
@Validated
public class IngestionProperties {

    @NotBlank
    private String resourcePattern = "classpath:/content/docs/*.json";
    private boolean rebuildOnStartup = true;
    private boolean includeClasspath = true;
    @Valid
    private final List<RemoteSource> remoteSources = new ArrayList<>();
    @Valid
    private final Schedule schedule = new Schedule();

    public String getResourcePattern() {
        return resourcePattern;
    }

    public void setResourcePattern(String resourcePattern) {
        if (resourcePattern == null || resourcePattern.isBlank()) {
            this.resourcePattern = "classpath:/content/docs/*.json";
            return;
        }
        this.resourcePattern = resourcePattern.trim();
    }

    public boolean isRebuildOnStartup() {
        return rebuildOnStartup;
    }

    public void setRebuildOnStartup(boolean rebuildOnStartup) {
        this.rebuildOnStartup = rebuildOnStartup;
    }

    public boolean isIncludeClasspath() {
        return includeClasspath;
    }

    public void setIncludeClasspath(boolean includeClasspath) {
        this.includeClasspath = includeClasspath;
    }

    public List<RemoteSource> getRemoteSources() {
        return remoteSources;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public static class Schedule {
        private boolean enabled = false;
        @Min(1_000L)
        private long fixedDelayMs = 21_600_000L;
        @Min(0L)
        private long initialDelayMs = 30_000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = Math.max(1_000L, fixedDelayMs);
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = Math.max(0L, initialDelayMs);
        }
    }

    public static class RemoteSource {
        private String id = "";
        private String url = "";
        private String format = "AUTO";
        private String sourceName = "Remote Source";
        private String sourceTag = "remote";
        private String version = "latest";
        private boolean enabled = true;
        private boolean failOnError = false;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id == null ? "" : id.trim();
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url == null ? "" : url.trim();
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = (format == null || format.isBlank()) ? "AUTO" : format.trim();
        }

        public String getSourceName() {
            return sourceName;
        }

        public void setSourceName(String sourceName) {
            this.sourceName = (sourceName == null || sourceName.isBlank()) ? "Remote Source" : sourceName.trim();
        }

        public String getSourceTag() {
            return sourceTag;
        }

        public void setSourceTag(String sourceTag) {
            this.sourceTag = (sourceTag == null || sourceTag.isBlank()) ? "remote" : sourceTag.trim();
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = (version == null || version.isBlank()) ? "latest" : version.trim();
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFailOnError() {
            return failOnError;
        }

        public void setFailOnError(boolean failOnError) {
            this.failOnError = failOnError;
        }

        @AssertTrue(message = "Enabled remote sources must define both id and url")
        public boolean hasRequiredFieldsWhenEnabled() {
            return !enabled || (!id.isBlank() && !url.isBlank());
        }
    }
}
