package com.example.javamcp.health;

import com.example.javamcp.ingest.IngestionProperties;
import com.example.javamcp.ingest.RemoteSourceIds;
import com.example.javamcp.model.IngestionSourceStatus;
import com.example.javamcp.search.IndexLifecycleService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RemoteSourcesHealthIndicator implements HealthIndicator {

    private final IndexLifecycleService indexLifecycleService;
    private final IngestionProperties ingestionProperties;

    RemoteSourcesHealthIndicator(IndexLifecycleService indexLifecycleService,
                                 IngestionProperties ingestionProperties) {
        this.indexLifecycleService = indexLifecycleService;
        this.ingestionProperties = ingestionProperties;
    }

    @Override
    public Health health() {
        Map<String, IngestionProperties.RemoteSource> configuredById = configuredRemoteSources();
        List<IngestionSourceStatus> statuses = indexLifecycleService.sourceStatuses().stream()
                .filter(status -> "remote".equalsIgnoreCase(status.sourceType()))
                .toList();

        if (statuses.isEmpty()) {
            return Health.up()
                    .withDetail("enabledRemoteSources", 0)
                    .withDetail("healthySources", 0)
                    .withDetail("requiredFailures", List.of())
                    .withDetail("optionalFailures", List.of())
                    .build();
        }

        List<Map<String, Object>> requiredFailures = new ArrayList<>();
        List<Map<String, Object>> optionalFailures = new ArrayList<>();
        int enabledRemoteSources = 0;
        int healthySources = 0;
        int neverLoadedSources = 0;

        for (IngestionSourceStatus status : statuses) {
            if (!status.enabled()) {
                continue;
            }

            enabledRemoteSources++;
            boolean hasError = status.lastError() != null && !status.lastError().isBlank();
            boolean neverLoaded = (status.lastLoadedAt() == null || status.lastLoadedAt().isBlank()) && !hasError;

            if (neverLoaded) {
                neverLoadedSources++;
                continue;
            }

            if (!hasError) {
                healthySources++;
                continue;
            }

            Map<String, Object> failure = toFailure(status);
            IngestionProperties.RemoteSource configuredSource = configuredById.get(status.sourceId());
            if (configuredSource != null && configuredSource.isFailOnError()) {
                requiredFailures.add(failure);
            } else {
                optionalFailures.add(failure);
            }
        }

        Health.Builder builder = requiredFailures.isEmpty() ? Health.up() : Health.down();
        return builder
                .withDetail("enabledRemoteSources", enabledRemoteSources)
                .withDetail("healthySources", healthySources)
                .withDetail("neverLoadedSources", neverLoadedSources)
                .withDetail("requiredFailures", List.copyOf(requiredFailures))
                .withDetail("optionalFailures", List.copyOf(optionalFailures))
                .build();
    }

    private Map<String, IngestionProperties.RemoteSource> configuredRemoteSources() {
        Map<String, IngestionProperties.RemoteSource> byId = new LinkedHashMap<>();
        for (IngestionProperties.RemoteSource source : ingestionProperties.getRemoteSources()) {
            byId.put(RemoteSourceIds.normalizedSourceId(source), source);
        }
        return byId;
    }

    private Map<String, Object> toFailure(IngestionSourceStatus status) {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("sourceId", status.sourceId());
        failure.put("sourceUrl", status.sourceUrl());
        failure.put("format", status.format());
        failure.put("lastLoadedAt", status.lastLoadedAt() == null ? "never" : status.lastLoadedAt());
        failure.put("message", status.lastError());
        return failure;
    }
}
