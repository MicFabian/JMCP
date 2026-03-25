package com.example.javamcp.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp.lucene")
public record LuceneProperties(String indexPath) {

    public LuceneProperties {
        if (indexPath == null || indexPath.isBlank()) {
            indexPath = "data/lucene";
        }
    }
}
