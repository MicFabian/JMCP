package com.example.javamcp.model;

public record SearchDiagnostics(
        String mode,
        String expandedQuery,
        int lexicalCandidates,
        int vectorCandidates,
        int elapsedMillis
) {
}
