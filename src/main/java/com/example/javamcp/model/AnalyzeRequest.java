package com.example.javamcp.model;

import jakarta.validation.constraints.NotBlank;

public record AnalyzeRequest(
        String fileName,
        @NotBlank String code
) {
}
