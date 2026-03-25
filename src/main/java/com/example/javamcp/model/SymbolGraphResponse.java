package com.example.javamcp.model;

import java.util.List;

public record SymbolGraphResponse(
        int nodeCount,
        int edgeCount,
        List<SymbolNode> nodes,
        List<SymbolEdge> edges
) {
}
