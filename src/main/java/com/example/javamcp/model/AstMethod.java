package com.example.javamcp.model;

public record AstMethod(
        String name,
        String signature,
        String returnType,
        String javadoc
) {
}
