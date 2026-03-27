package com.example.javamcp.model;

public record MigrationAssistantRequest(
        String buildFile,
        String buildFilePath,
        String code,
        Integer targetJavaVersion,
        String targetSpringBootVersion,
        Boolean includeDocs
) {
}
