package com.example.javamcp.search

import com.example.javamcp.ingest.IngestionProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import spock.lang.Specification

class ConditionalJobsSpec extends Specification {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(IngestionProperties) { new IngestionProperties() }
            .withBean(IndexLifecycleService) { Stub(IndexLifecycleService) }
            .withUserConfiguration(TestConfiguration)

    def 'should only register startup rebuild runner when enabled'() {
        expect:
        contextRunner
                .withPropertyValues('mcp.ingest.rebuild-on-startup=true')
                .run { context ->
                    assert context.getBeansOfType(IndexBootstrapRunner).size() == 1
                }

        contextRunner
                .withPropertyValues('mcp.ingest.rebuild-on-startup=false')
                .run { context ->
                    assert context.getBeansOfType(IndexBootstrapRunner).isEmpty()
                }
    }

    def 'should only register scheduled refresh job when scheduling is enabled'() {
        expect:
        contextRunner
                .withPropertyValues('mcp.ingest.schedule.enabled=true')
                .run { context ->
                    assert context.getBeansOfType(ScheduledIndexRefreshJob).size() == 1
                }

        contextRunner
                .withPropertyValues('mcp.ingest.schedule.enabled=false')
                .run { context ->
                    assert context.getBeansOfType(ScheduledIndexRefreshJob).isEmpty()
                }
    }

    @Configuration(proxyBeanMethods = false)
    @Import([IndexBootstrapRunner, ScheduledIndexRefreshJob])
    static class TestConfiguration {
    }
}
