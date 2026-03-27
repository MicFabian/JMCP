package com.example.javamcp.model;

public record MigrationReference(
        String libraryId,
        String title,
        String sourceUrl,
        String version,
        float score
) {
}
