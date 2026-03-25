package com.example.javamcp.model;

public record RuleIssue(
        String rule,
        int line,
        String severity,
        String message,
        String suggestion
) {
}
