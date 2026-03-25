package com.example.javamcp.model;

import jakarta.validation.constraints.NotBlank;

public record AstRequest(@NotBlank String code) {
}
