package com.example.javamcp.model;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record MigrationAssistantRequest(
        String buildFile,
        String buildFilePath,
        String code,
        @Min(8) Integer targetJavaVersion,
        @Pattern(regexp = "^$|\\d+(?:\\.\\d+){0,2}(?:[-A-Za-z0-9.]+)?$", message = "targetSpringBootVersion must look like a Spring Boot version")
        String targetSpringBootVersion,
        Boolean includeDocs
) {
    @AssertTrue(message = "Either buildFile or code must be provided")
    public boolean hasBuildFileOrCode() {
        return hasText(buildFile) || hasText(code);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
