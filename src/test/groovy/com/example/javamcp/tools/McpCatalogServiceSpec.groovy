package com.example.javamcp.tools

import com.example.javamcp.ingest.IngestionService
import com.example.javamcp.model.IndexStatsResponse
import com.example.javamcp.model.IngestedDocument
import com.example.javamcp.search.IndexLifecycleService
import spock.lang.Specification

class McpCatalogServiceSpec extends Specification {

    def 'should expose tools resources prompts and rules'() {
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
        def lifecycle = Stub(IndexLifecycleService) {
            currentStats() >> new IndexStatsResponse(1, ['4.0.0'], ['security', 'spring-boot'], ['Spring Security Reference'], '2026-02-26T00:00:00Z')
        }
        def service = new McpCatalogService(ingestion, lifecycle)

        when:
        def tools = service.listTools()
        def rules = service.listToolRules()
        def resources = service.listResources()
        def resource = service.getResource('spring-boot-csrf')
        def prompts = service.listPrompts()
        def manifest = service.manifest()

        then:
        tools*.name().contains('query-docs')
        tools*.name().contains('migration-assistant')
        rules*.id().contains('java-library-docs')
        rules*.id().contains('project-migration-audit')
        resources*.resourceId() == ['spring-boot-csrf']
        resource.resource().uri() == 'mcp://docs/spring-boot-csrf'
        prompts*.id().contains('resolve-then-query')
        prompts*.id().contains('gradle-migration-audit')
        manifest.serverName() == 'java-mcp'
        manifest.tools()*.name().contains('query-docs')
        manifest.tools()*.name().contains('migration-assistant')
        manifest.resources()*.resourceId() == ['spring-boot-csrf']
    }
}
