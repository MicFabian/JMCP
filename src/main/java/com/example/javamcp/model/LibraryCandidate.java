package com.example.javamcp.model;

import java.util.List;

public record LibraryCandidate(
        String libraryId,
        String name,
        String summary,
        int documentCount,
        List<String> versions,
        List<String> sources,
        float score
) {
}
