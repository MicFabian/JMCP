package com.example.javamcp.model;

import java.util.List;

public record AstResponse(
        int classCount,
        List<AstClass> classes
) {
}
