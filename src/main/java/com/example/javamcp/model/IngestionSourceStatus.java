package com.example.javamcp.model;

public record IngestionSourceStatus(
        String sourceId,
        String sourceType,
        String sourceUrl,
        String format,
        boolean enabled,
        int loadedDocuments,
        String lastLoadedAt,
        String lastError
) {
}
