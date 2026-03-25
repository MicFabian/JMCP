package com.example.javamcp.model;

public record PromptTemplate(
        String id,
        String name,
        String description,
        String template
) {
}
