package com.example.javamcp.model;

import java.util.List;

public record LibraryDoc(
        String id,
        String title,
        String excerpt,
        String source,
        String sourceUrl,
        String version,
        float score,
        float retrievalScore,
        float rerankScore,
        List<String> matchedTerms
) {
}
