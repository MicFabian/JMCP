package com.example.javamcp.tools

import com.example.javamcp.ingest.IngestionService
import com.example.javamcp.model.IngestedDocument
import com.example.javamcp.model.SearchResponse
import com.example.javamcp.model.SearchResult
import com.example.javamcp.search.LuceneSearchService
import com.example.javamcp.search.QueryExpansionService
import com.example.javamcp.search.SearchProperties
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

class LibraryToolsServiceSpec extends Specification {

    def 'should resolve canonical spring security library id'() {
        given:
        def ingestion = Stub(IngestionService) {
            loadNormalizedDocuments() >> [
                    new IngestedDocument(
                            'doc-1',
                            'Enable CSRF Protection',
                            '4.0.0',
                            ['security', 'spring-boot'],
                            'By default, Spring Security enables CSRF protection.',
                            'Spring Security Reference',
                            'https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html'
                    ),
                    new IngestedDocument(
                            'doc-2',
                            'Constructor Injection',
                            '4.0.0',
                            ['spring-core'],
                            'Prefer constructor injection.',
                            'Spring Framework Reference',
                            'https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html'
                    )
            ]
        }
        def lucene = Stub(LuceneSearchService)
        def service = new LibraryToolsService(ingestion, lucene, new QueryExpansionService(new SearchProperties()), new SimpleMeterRegistry())

        when:
        def response = service.resolveLibraryId('spring security csrf', 'spring security', 'csrf', 3)

        then:
        response.count() >= 1
        response.libraries().first().libraryId() == '/spring-projects/spring-security'
    }

    def 'should return scoped docs and context for resolved library id'() {
        given:
        def ingestion = Stub(IngestionService) {
            loadNormalizedDocuments() >> [
                    new IngestedDocument(
                            'spring-boot-csrf',
                            'Enable CSRF Protection',
                            '4.0.0',
                            ['security', 'spring-boot'],
                            'By default, Spring Security enables CSRF protection.',
                            'Spring Security Reference',
                            'https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html'
                    )
            ]
        }
        def lucene = Stub(LuceneSearchService) {
            search(_) >> new SearchResponse(
                    'csrf',
                    1,
                    [new SearchResult(
                            'spring-boot-csrf',
                            'Enable CSRF Protection',
                            'By default, Spring Security enables CSRF protection.',
                            'Spring Security Reference',
                            'https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html',
                            '4.0.0',
                            0.92f
                    )],
                    null
            )
        }
        def service = new LibraryToolsService(ingestion, lucene, new QueryExpansionService(new SearchProperties()), new SimpleMeterRegistry())

        when:
        def response = service.queryDocs('/spring-projects/spring-security', 'csrf', 1000, 5, '4.0.0', null, 0.65d)

        then:
        response.count() == 1
        response.documents().first().id() == 'spring-boot-csrf'
        response.documents().first().matchedTerms() == ['csrf']
        response.strategy() == 'hybrid-rerank-dedup'
        response.context().contains('Source: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html')
    }
}
