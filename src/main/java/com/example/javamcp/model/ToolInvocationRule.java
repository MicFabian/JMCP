package com.example.javamcp.model;

import java.util.List;

public record ToolInvocationRule(
        String id,
        String description,
        List<String> triggerPatterns,
        String toolName,
        int priority
) {
}
