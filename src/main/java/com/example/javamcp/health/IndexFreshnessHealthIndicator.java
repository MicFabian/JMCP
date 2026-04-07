package com.example.javamcp.health;

import com.example.javamcp.config.McpHealthProperties;
import com.example.javamcp.ingest.IngestionProperties;
import com.example.javamcp.model.IndexStatsResponse;
import com.example.javamcp.search.IndexLifecycleService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

final class IndexFreshnessHealthIndicator implements HealthIndicator {

    private final IndexLifecycleService indexLifecycleService;
    private final IngestionProperties ingestionProperties;
    private final McpHealthProperties healthProperties;
    private final Clock clock;

    IndexFreshnessHealthIndicator(IndexLifecycleService indexLifecycleService,
                                  IngestionProperties ingestionProperties,
                                  McpHealthProperties healthProperties,
                                  Clock clock) {
        this.indexLifecycleService = indexLifecycleService;
        this.ingestionProperties = ingestionProperties;
        this.healthProperties = healthProperties;
        this.clock = clock;
    }

    @Override
    public Health health() {
        IndexStatsResponse stats = indexLifecycleService.currentStats();
        String lastIndexedAt = stats.lastIndexedAt();

        if (lastIndexedAt == null || lastIndexedAt.isBlank()) {
            return healthWhenIndexTimestampMissing(stats);
        }

        try {
            Instant indexedAt = Instant.parse(lastIndexedAt);
            Duration age = Duration.between(indexedAt, Instant.now(clock));
            boolean fresh = age.compareTo(healthProperties.getMaxIndexAge()) <= 0;
            Health.Builder builder = fresh ? Health.up() : Health.down();
            return builder
                    .withDetail("fresh", fresh)
                    .withDetail("lastIndexedAt", lastIndexedAt)
                    .withDetail("age", age.toString())
                    .withDetail("maxIndexAge", healthProperties.getMaxIndexAge().toString())
                    .withDetail("documentCount", stats.documentCount())
                    .build();
        } catch (DateTimeParseException ex) {
            return Health.down(ex)
                    .withDetail("fresh", false)
                    .withDetail("lastIndexedAt", lastIndexedAt)
                    .withDetail("reason", "Invalid lastIndexedAt timestamp")
                    .withDetail("documentCount", stats.documentCount())
                    .build();
        }
    }

    private Health healthWhenIndexTimestampMissing(IndexStatsResponse stats) {
        String reason;
        Health.Builder builder;

        if (ingestionProperties.isRebuildOnStartup()) {
            builder = Health.down();
            reason = "No successful index build recorded although startup rebuild is enabled";
        } else if (ingestionProperties.getSchedule().isEnabled()) {
            builder = Health.up();
            reason = "No successful index build recorded yet; waiting for scheduled refresh";
        } else {
            builder = Health.up();
            reason = "No successful index build recorded yet; manual rebuild mode";
        }

        return builder
                .withDetail("fresh", false)
                .withDetail("lastIndexedAt", "never")
                .withDetail("maxIndexAge", healthProperties.getMaxIndexAge().toString())
                .withDetail("documentCount", stats.documentCount())
                .withDetail("reason", reason)
                .build();
    }
}
