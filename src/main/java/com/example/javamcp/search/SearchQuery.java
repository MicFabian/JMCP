package com.example.javamcp.search;

import java.util.List;

public record SearchQuery(
        String query,
        Integer limit,
        String version,
        List<String> tags,
        String source,
        SearchMode mode,
        boolean includeDiagnostics
) {

    public SearchQuery {
        if (tags == null) {
            tags = List.of();
        }
        if (mode == null) {
            mode = SearchMode.HYBRID;
        }
    }
}
