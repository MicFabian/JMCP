package com.example.javamcp.health

import com.example.javamcp.config.McpHealthProperties
import com.example.javamcp.ingest.IngestionProperties
import com.example.javamcp.model.IndexStatsResponse
import com.example.javamcp.model.IngestionSourceStatus
import com.example.javamcp.search.IndexLifecycleService
import spock.lang.Specification

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class JmcpHealthIndicatorsSpec extends Specification {

    private final Clock fixedClock = Clock.fixed(Instant.parse('2026-04-07T10:00:00Z'), ZoneOffset.UTC)

    def 'should report fresh index as up'() {
        given:
        def ingestionProperties = new IngestionProperties()
        def healthProperties = new McpHealthProperties()
        healthProperties.maxIndexAge = Duration.ofHours(24)
        def lifecycle = Stub(IndexLifecycleService) {
            currentStats() >> new IndexStatsResponse(12, ['4.0.0'], ['spring'], ['Spring Reference'], '2026-04-07T06:00:00Z')
        }

        when:
        def health = new IndexFreshnessHealthIndicator(lifecycle, ingestionProperties, healthProperties, fixedClock).health()

        then:
        health.status.code == 'UP'
        health.details.fresh == true
        health.details.documentCount == 12
    }

    def 'should report stale index as down'() {
        given:
        def ingestionProperties = new IngestionProperties()
        def healthProperties = new McpHealthProperties()
        healthProperties.maxIndexAge = Duration.ofHours(6)
        def lifecycle = Stub(IndexLifecycleService) {
            currentStats() >> new IndexStatsResponse(12, ['4.0.0'], ['spring'], ['Spring Reference'], '2026-04-06T22:00:00Z')
        }

        when:
        def health = new IndexFreshnessHealthIndicator(lifecycle, ingestionProperties, healthProperties, fixedClock).health()

        then:
        health.status.code == 'DOWN'
        health.details.fresh == false
    }

    def 'should stay up while waiting for scheduled refresh to build first index'() {
        given:
        def ingestionProperties = new IngestionProperties()
        ingestionProperties.rebuildOnStartup = false
        ingestionProperties.schedule.enabled = true
        def healthProperties = new McpHealthProperties()
        def lifecycle = Stub(IndexLifecycleService) {
            currentStats() >> new IndexStatsResponse(0, [], [], [], null)
        }

        when:
        def health = new IndexFreshnessHealthIndicator(lifecycle, ingestionProperties, healthProperties, fixedClock).health()

        then:
        health.status.code == 'UP'
        health.details.reason == 'No successful index build recorded yet; waiting for scheduled refresh'
    }

    def 'should report required remote source failures as down'() {
        given:
        def ingestionProperties = new IngestionProperties()
        ingestionProperties.remoteSources << remoteSource('spring-docs', 'https://docs.spring.io', true)
        def lifecycle = Stub(IndexLifecycleService) {
            sourceStatuses() >> [
                    new IngestionSourceStatus('spring-docs', 'remote', 'https://docs.spring.io', 'HTML', true, 0, '2026-04-07T09:30:00Z', 'HTTP 503')
            ]
        }

        when:
        def health = new RemoteSourcesHealthIndicator(lifecycle, ingestionProperties).health()

        then:
        health.status.code == 'DOWN'
        ((List<Map<String, Object>>) health.details.requiredFailures).size() == 1
        ((List<Map<String, Object>>) health.details.optionalFailures).isEmpty()
    }

    def 'should keep optional remote source failures visible without failing health'() {
        given:
        def ingestionProperties = new IngestionProperties()
        ingestionProperties.remoteSources << remoteSource('spring-docs', 'https://docs.spring.io', false)
        def lifecycle = Stub(IndexLifecycleService) {
            sourceStatuses() >> [
                    new IngestionSourceStatus('spring-docs', 'remote', 'https://docs.spring.io', 'HTML', true, 0, '2026-04-07T09:30:00Z', 'HTTP 503')
            ]
        }

        when:
        def health = new RemoteSourcesHealthIndicator(lifecycle, ingestionProperties).health()

        then:
        health.status.code == 'UP'
        ((List<Map<String, Object>>) health.details.requiredFailures).isEmpty()
        ((List<Map<String, Object>>) health.details.optionalFailures).size() == 1
    }

    private static IngestionProperties.RemoteSource remoteSource(String id, String url, boolean failOnError) {
        def source = new IngestionProperties.RemoteSource()
        source.id = id
        source.url = url
        source.enabled = true
        source.failOnError = failOnError
        return source
    }
}
