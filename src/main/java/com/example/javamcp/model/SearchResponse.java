package com.example.javamcp.model;

import java.util.List;

public record SearchResponse(
        String query,
        int count,
        List<SearchResult> results,
        SearchDiagnostics diagnostics
) {
}
