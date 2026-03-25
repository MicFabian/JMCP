package com.example.javamcp.model;

import java.util.List;

public record AnalyzeResponse(
        String file,
        int issueCount,
        List<RuleIssue> issues
) {
}
