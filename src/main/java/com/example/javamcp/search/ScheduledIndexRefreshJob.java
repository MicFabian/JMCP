package com.example.javamcp.search;

import com.example.javamcp.ingest.IngestionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "mcp.ingest.schedule", name = "enabled", havingValue = "true")
public class ScheduledIndexRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledIndexRefreshJob.class);

    private final IngestionProperties ingestionProperties;
    private final IndexLifecycleService indexLifecycleService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ScheduledIndexRefreshJob(IngestionProperties ingestionProperties,
                                    IndexLifecycleService indexLifecycleService) {
        this.ingestionProperties = ingestionProperties;
        this.indexLifecycleService = indexLifecycleService;
    }

    @Scheduled(
            initialDelayString = "${mcp.ingest.schedule.initial-delay-ms:30000}",
            fixedDelayString = "${mcp.ingest.schedule.fixed-delay-ms:21600000}"
    )
    public void refreshScheduled() {
        if (!ingestionProperties.getSchedule().isEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("Skipping scheduled index refresh because a previous refresh is still running");
            return;
        }

        try {
            var stats = indexLifecycleService.rebuildIndex();
            log.info("Scheduled index refresh complete: {} documents indexed", stats.documentCount());
        } catch (Exception e) {
            log.error("Scheduled index refresh failed: {}", e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }
}
