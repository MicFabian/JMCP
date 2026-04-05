package com.example.javamcp.search

import com.example.javamcp.model.IngestedDocument
import com.example.javamcp.observability.OperationObservationService
import io.micrometer.observation.ObservationRegistry
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor
import spock.lang.Specification

import java.nio.file.Files
import java.util.concurrent.Executors

class LuceneSearchServiceSpec extends Specification {

    private def tempDir
    private def executor

    def setup() {
        tempDir = Files.createTempDirectory('lucene-spec')
        executor = Executors.newVirtualThreadPerTaskExecutor()
    }

    def cleanup() {
        executor.shutdownNow()
        tempDir.toFile().deleteDir()
    }

    def 'should return indexed documents with hybrid mode'() {
        given:
        def searchProperties = new SearchProperties()
        def service = new LuceneSearchService(
                new LuceneProperties(tempDir.resolve('lucene').toString()),
                new EmbeddingService(),
                new QueryExpansionService(searchProperties),
                searchProperties,
                new ConcurrentTaskExecutor(executor),
                new OperationObservationService(ObservationRegistry.create())
        )

        service.rebuildIndex([
                new IngestedDocument(
                        'doc-1',
                        'Constructor Injection',
                        '4.0.0',
                        ['spring', 'best-practice'],
                        'Use constructor injection in Spring services.',
                        'Ref',
                        'https://example.com/constructor'
                )
        ])

        when:
        def response = service.search(new SearchQuery('constructor', 5, null, [], null, SearchMode.HYBRID, true))

        then:
        response.count() > 0
        response.results().first().id() == 'doc-1'
        response.diagnostics() != null
        response.diagnostics().mode() == 'HYBRID'
    }

    def 'should support exact filters'() {
        given:
        def searchProperties = new SearchProperties()
        def service = new LuceneSearchService(
                new LuceneProperties(tempDir.resolve('lucene-filtered').toString()),
                new EmbeddingService(),
                new QueryExpansionService(searchProperties),
                searchProperties,
                new ConcurrentTaskExecutor(executor),
                new OperationObservationService(ObservationRegistry.create())
        )

        service.rebuildIndex([
                new IngestedDocument(
                        'doc-1',
                        'Constructor Injection',
                        '4.0.0',
                        ['spring', 'best-practice'],
                        'Use constructor injection in Spring services.',
                        'Spring Framework Reference',
                        'https://example.com/constructor'
                ),
                new IngestedDocument(
                        'doc-2',
                        'CSRF Basics',
                        '3.3.0',
                        ['security'],
                        'By default, Spring Security enables CSRF.',
                        'Spring Security Reference',
                        'https://example.com/csrf'
                )
        ])

        when:
        def response = service.search(new SearchQuery(
                'spring',
                10,
                '4.0.0',
                ['best-practice'],
                'Spring Framework Reference',
                SearchMode.LEXICAL,
                false
        ))

        then:
        response.count() == 1
        response.results().first().id() == 'doc-1'
    }

    def 'should skip vector embedding work for lexical-only searches'() {
        given:
        def searchProperties = new SearchProperties()
        def embeddingService = Mock(EmbeddingService)
        embeddingService.embed(_ as String) >> ([0.25f, 0.75f] as float[])
        def service = new LuceneSearchService(
                new LuceneProperties(tempDir.resolve('lucene-lexical-only').toString()),
                embeddingService,
                new QueryExpansionService(searchProperties),
                searchProperties,
                new ConcurrentTaskExecutor(executor),
                new OperationObservationService(ObservationRegistry.create())
        )

        service.rebuildIndex([
                new IngestedDocument(
                        'doc-1',
                        'Constructor Injection',
                        '4.0.0',
                        ['spring'],
                        'Use constructor injection in Spring services.',
                        'Ref',
                        'https://example.com/constructor'
                )
        ])

        when:
        def response = service.search(new SearchQuery('constructor', 5, null, [], null, SearchMode.LEXICAL, true))

        then:
        0 * embeddingService.embed(_ as String)
        response.count() == 1
        response.diagnostics().vectorCandidates() == 0
    }

    def 'should refresh search runtime after index rebuild'() {
        given:
        def searchProperties = new SearchProperties()
        def service = new LuceneSearchService(
                new LuceneProperties(tempDir.resolve('lucene-refresh').toString()),
                new EmbeddingService(),
                new QueryExpansionService(searchProperties),
                searchProperties,
                new ConcurrentTaskExecutor(executor),
                new OperationObservationService(ObservationRegistry.create())
        )

        service.rebuildIndex([
                new IngestedDocument(
                        'doc-a',
                        'Guide A',
                        '4.0.0',
                        ['spring'],
                        'shared-term',
                        'Ref',
                        'https://example.com/a'
                )
        ])

        when:
        def first = service.search(new SearchQuery('shared-term', 5, null, [], null, SearchMode.HYBRID, false))

        and:
        service.rebuildIndex([
                new IngestedDocument(
                        'doc-b',
                        'Guide B',
                        '4.0.0',
                        ['spring'],
                        'shared-term',
                        'Ref',
                        'https://example.com/b'
                )
        ])
        def second = service.search(new SearchQuery('shared-term', 5, null, [], null, SearchMode.HYBRID, false))

        then:
        first.count() == 1
        first.results().first().id() == 'doc-a'
        second.count() == 1
        second.results().first().id() == 'doc-b'
    }
}
