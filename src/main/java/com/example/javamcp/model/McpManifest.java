package com.example.javamcp.model;

import java.util.List;

public record McpManifest(
        String serverName,
        String version,
        String generatedAt,
        List<ToolDescriptor> tools,
        List<ToolInvocationRule> toolRules,
        List<McpResourceDescriptor> resources,
        List<PromptTemplate> prompts
) {
}
