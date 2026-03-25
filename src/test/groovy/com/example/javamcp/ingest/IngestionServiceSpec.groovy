package com.example.javamcp.ingest

import com.example.javamcp.model.IngestedDocument
import spock.lang.Specification

class IngestionServiceSpec extends Specification {

    def 'should cache normalized documents between reads'() {
        given:
        def loader = Mock(ResourceDocumentLoader)
        def service = new IngestionService(loader)

        1 * loader.loadDocuments() >> [
                new IngestedDocument(' doc-1 ', ' Title ', '4.0.0', [' spring ', 'spring', 'security'], 'Body   text', ' Source ', 'https://example.com/a ')
        ]

        when:
        def first = service.loadNormalizedDocuments()
        def second = service.loadNormalizedDocuments()

        then:
        first.is(second)
        first[0].id() == 'doc-1'
        first[0].tags() == ['spring', 'security']
        first[0].content() == 'Body text'
    }

    def 'should reload documents after cache invalidation'() {
        given:
        def loader = Mock(ResourceDocumentLoader)
        def service = new IngestionService(loader)

        1 * loader.loadDocuments() >> [
                new IngestedDocument('doc-1', 'Title 1', '4.0.0', ['spring'], 'Body 1', 'Source', 'https://example.com/a')
        ]
        1 * loader.loadDocuments() >> [
                new IngestedDocument('doc-2', 'Title 2', '4.0.0', ['security'], 'Body 2', 'Source', 'https://example.com/b')
        ]

        when:
        def first = service.loadNormalizedDocuments()
        service.invalidateCache()
        def second = service.loadNormalizedDocuments()

        then:
        first*.id() == ['doc-1']
        second*.id() == ['doc-2']
    }
}
