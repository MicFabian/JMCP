package com.example.javamcp.search

import spock.lang.Specification

class QueryExpansionServiceSpec extends Specification {

    def 'should expand configured synonyms'() {
        given:
        def properties = new SearchProperties()
        def service = new QueryExpansionService(properties)

        when:
        def expanded = service.expand('csrf db')

        then:
        expanded.contains('csrf')
        expanded.contains('cross site request forgery')
        expanded.contains('database')
    }
}
