package com.example.javamcp.model;

public record SearchResult(
        String id,
        String title,
        String snippet,
        String source,
        String sourceUrl,
        String version,
        float score
) {
}
