package com.example.javamcp.model;

import java.util.List;

public record LibraryDocsResponse(
        String libraryId,
        String libraryName,
        String topic,
        String strategy,
        float alpha,
        int count,
        int approxTokens,
        String context,
        List<LibraryDoc> documents
) {
}
