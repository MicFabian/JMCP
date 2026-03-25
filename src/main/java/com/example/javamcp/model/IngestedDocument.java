package com.example.javamcp.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record IngestedDocument(
        @NotBlank String id,
        @NotBlank String title,
        @NotBlank String version,
        List<String> tags,
        @NotBlank String content,
        @NotBlank String source,
        @NotBlank String sourceUrl
) {
}
