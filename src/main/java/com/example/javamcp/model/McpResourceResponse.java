package com.example.javamcp.model;

public record McpResourceResponse(
        McpResourceDescriptor resource,
        String content
) {
}
