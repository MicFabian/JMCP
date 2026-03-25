package com.example.javamcp;

import com.example.javamcp.config.McpSecurityProperties;
import com.example.javamcp.config.McpIngressProperties;
import com.example.javamcp.ingest.IngestionProperties;
import com.example.javamcp.search.LuceneProperties;
import com.example.javamcp.search.SearchProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableConfigurationProperties({
        LuceneProperties.class,
        IngestionProperties.class,
        SearchProperties.class,
        McpSecurityProperties.class,
        McpIngressProperties.class
})
@EnableCaching
public class JavaMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavaMcpApplication.class, args);
    }
}
