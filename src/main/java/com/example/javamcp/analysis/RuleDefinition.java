package com.example.javamcp.analysis;

public record RuleDefinition(
        String id,
        String description,
        RuleMatchType matchType,
        String pattern,
        String fix,
        String severity,
        String target,
        Boolean enabled
) {

    public RuleDefinition {
        if (matchType == null) {
            matchType = RuleMatchType.CONTAINS;
        }
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
    }
}
