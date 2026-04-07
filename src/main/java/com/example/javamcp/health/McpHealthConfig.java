package com.example.javamcp.health;

import com.example.javamcp.config.McpHealthProperties;
import com.example.javamcp.ingest.IngestionProperties;
import com.example.javamcp.search.IndexLifecycleService;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class McpHealthConfig {

    @Bean("jmcp")
    public CompositeHealthContributor jmcpHealthContributor(IndexLifecycleService indexLifecycleService,
                                                            IngestionProperties ingestionProperties,
                                                            McpHealthProperties healthProperties,
                                                            Clock clock) {
        return CompositeHealthContributor.fromMap(Map.of(
                "indexFreshness", new IndexFreshnessHealthIndicator(indexLifecycleService, ingestionProperties, healthProperties, clock),
                "remoteSources", new RemoteSourcesHealthIndicator(indexLifecycleService, ingestionProperties)
        ));
    }
}
