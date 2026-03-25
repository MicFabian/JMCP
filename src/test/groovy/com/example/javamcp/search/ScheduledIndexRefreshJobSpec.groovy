package com.example.javamcp.search

import com.example.javamcp.ingest.IngestionProperties
import com.example.javamcp.model.IndexStatsResponse
import spock.lang.Specification

class ScheduledIndexRefreshJobSpec extends Specification {

    def 'should skip scheduled refresh when schedule is disabled'() {
        given:
        def ingestionProperties = new IngestionProperties()
        ingestionProperties.schedule.enabled = false
        def lifecycle = Mock(IndexLifecycleService)
        def job = new ScheduledIndexRefreshJob(ingestionProperties, lifecycle)

        when:
        job.refreshScheduled()

        then:
        0 * lifecycle._
    }

    def 'should run scheduled refresh when schedule is enabled'() {
        given:
        def ingestionProperties = new IngestionProperties()
        ingestionProperties.schedule.enabled = true
        def lifecycle = Mock(IndexLifecycleService)
        lifecycle.rebuildIndex() >> new IndexStatsResponse(2, ['4.0.0'], ['spring'], ['remote'], '2026-03-25T20:00:00Z')
        def job = new ScheduledIndexRefreshJob(ingestionProperties, lifecycle)

        when:
        job.refreshScheduled()

        then:
        1 * lifecycle.rebuildIndex()
    }
}
