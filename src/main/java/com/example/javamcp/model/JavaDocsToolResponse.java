package com.example.javamcp.model;

import java.util.List;

public record JavaDocsToolResponse(
        String query,
        String libraryHint,
        String resolvedLibraryId,
        String resolvedLibraryName,
        String strategy,
        int count,
        String context,
        List<LibraryDoc> documents
) {
}
