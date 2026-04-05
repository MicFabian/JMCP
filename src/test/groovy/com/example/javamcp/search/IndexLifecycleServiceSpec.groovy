package com.example.javamcp.search

import com.example.javamcp.ingest.IngestionService
import com.example.javamcp.model.IngestedDocument
import com.example.javamcp.observability.OperationObservationService
import io.micrometer.observation.ObservationRegistry
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor
import spock.lang.Specification

import java.nio.file.Files
import java.util.concurrent.Executors

class IndexLifecycleServiceSpec extends Specification {

    private def tempDir
    private def executor

    def setup() {
        tempDir = Files.createTempDirectory('index-lifecycle-spec')
        executor = Executors.newVirtualThreadPerTaskExecutor()
    }

    def cleanup() {
        executor.shutdownNow()
        tempDir.toFile().deleteDir()
    }

    def 'should produce index stats after rebuild'() {
        given:
        def searchProperties = new SearchProperties()
        def lucene = new LuceneSearchService(
                new LuceneProperties(tempDir.resolve('lucene').toString()),
                new EmbeddingService(),
                new QueryExpansionService(searchProperties),
                searchProperties,
                new ConcurrentTaskExecutor(executor),
                new OperationObservationService(ObservationRegistry.create())
        )

        def ingestion = Stub(IngestionService) {
            reloadNormalizedDocuments() >> [
                    new IngestedDocument('doc-1', 'Title', '4.0.0', ['spring'], 'Body', 'SourceA', 'https://example.com/a'),
                    new IngestedDocument('doc-2', 'Title2', '4.0.0', ['security'], 'Body', 'SourceB', 'https://example.com/b')
            ]
        }

        def lifecycle = new IndexLifecycleService(ingestion, lucene, new OperationObservationService(ObservationRegistry.create()))

        when:
        def stats = lifecycle.rebuildIndex()

        then:
        stats.documentCount() == 2
        stats.versions() == ['4.0.0']
        stats.tags().containsAll(['spring', 'security'])
        stats.lastIndexedAt() != null
    }
}
