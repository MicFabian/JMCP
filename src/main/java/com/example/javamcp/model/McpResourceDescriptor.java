package com.example.javamcp.model;

import java.util.List;

public record McpResourceDescriptor(
        String resourceId,
        String uri,
        String title,
        String version,
        List<String> tags,
        String source,
        String sourceUrl
) {
}
