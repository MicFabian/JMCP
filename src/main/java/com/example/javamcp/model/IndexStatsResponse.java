package com.example.javamcp.model;

import java.util.List;

public record IndexStatsResponse(
        int documentCount,
        List<String> versions,
        List<String> tags,
        List<String> sources,
        String lastIndexedAt
) {
}
