package com.example.javamcp.search;

import com.example.javamcp.ingest.IngestionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class IndexBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexBootstrapRunner.class);

    private final IngestionProperties ingestionProperties;
    private final IndexLifecycleService indexLifecycleService;

    public IndexBootstrapRunner(IngestionProperties ingestionProperties,
                                IndexLifecycleService indexLifecycleService) {
        this.ingestionProperties = ingestionProperties;
        this.indexLifecycleService = indexLifecycleService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!ingestionProperties.rebuildOnStartup()) {
            log.info("Skipping startup index rebuild because mcp.ingest.rebuild-on-startup=false");
            return;
        }

        var stats = indexLifecycleService.rebuildIndex();
        log.info("Indexed {} MCP documents (versions: {})", stats.documentCount(), stats.versions());
    }
}
