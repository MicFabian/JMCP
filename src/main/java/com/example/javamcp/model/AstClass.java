package com.example.javamcp.model;

import java.util.List;

public record AstClass(
        String name,
        String packageName,
        List<AstMethod> methods
) {
}
