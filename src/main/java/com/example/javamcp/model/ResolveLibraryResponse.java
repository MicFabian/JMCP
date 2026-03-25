package com.example.javamcp.model;

import java.util.List;

public record ResolveLibraryResponse(
        String query,
        int count,
        List<LibraryCandidate> libraries
) {
}
