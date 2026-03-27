package com.example.javamcp.model;

import java.util.List;

public record MigrationAssistantResponse(
        String buildTool,
        String detectedJavaVersion,
        String targetJavaVersion,
        String detectedSpringBootVersion,
        String targetSpringBootVersion,
        int issueCount,
        List<MigrationFinding> findings,
        List<String> recommendedActions,
        List<MigrationReference> references
) {
}
