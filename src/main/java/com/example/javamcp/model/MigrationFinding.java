package com.example.javamcp.model;

public record MigrationFinding(
        String code,
        String severity,
        String message,
        String recommendation,
        String detectedValue,
        String targetValue
) {
}
