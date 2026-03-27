package com.example.javamcp.api

import com.example.javamcp.model.LibraryCandidate
import com.example.javamcp.model.LibraryDoc
import com.example.javamcp.model.LibraryDocsResponse
import com.example.javamcp.model.MigrationAssistantRequest
import com.example.javamcp.model.MigrationAssistantResponse
import com.example.javamcp.model.MigrationFinding
import com.example.javamcp.model.MigrationReference
import com.example.javamcp.model.ResolveLibraryResponse
import com.example.javamcp.model.ToolInvocationRule
import com.example.javamcp.search.SearchMode
import com.example.javamcp.tools.LibraryToolsService
import com.example.javamcp.tools.MigrationAssistantService
import com.example.javamcp.tools.McpCatalogService
import spock.lang.Specification

class ToolsControllerSpec extends Specification {

    def 'should delegate resolve/query/migration tool endpoints'() {
        given:
        def libraryTools = Stub(LibraryToolsService) {
            resolveLibraryId(_, _, _, _) >> new ResolveLibraryResponse(
                    'spring security csrf',
                    1,
                    [new LibraryCandidate('/spring-projects/spring-security', 'Spring Security', 'Enable CSRF Protection', 1, ['4.0.0'], ['Spring Security Reference'], 0.9f)]
            )
            queryDocs(_, _, _, _, _, _, _) >> new LibraryDocsResponse(
                    '/spring-projects/spring-security',
                    'Spring Security',
                    'csrf',
                    'hybrid-rerank-dedup',
                    0.65f,
                    1,
                    80,
                    '# Spring Security...',
                    [new LibraryDoc('spring-boot-csrf', 'Enable CSRF Protection', 'By default...', 'Spring Security Reference', 'https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html', '4.0.0', 0.9f, 0.8f, 0.9f, ['csrf'])]
            )
        }
        def migration = Stub(MigrationAssistantService) {
            assess(_) >> new MigrationAssistantResponse(
                    'GRADLE_GROOVY',
                    '17',
                    '25',
                    '3.3.2',
                    '4.0.0',
                    1,
                    [new MigrationFinding('java-version-upgrade-required', 'HIGH', 'Upgrade Java', 'Use Java 25 toolchain', '17', '25')],
                    ['Use Java 25 toolchain'],
                    [new MigrationReference('/openjdk/jdk', 'Virtual Threads', 'https://openjdk.org/jeps/444', '25', 0.9f)]
            )
        }
        def catalog = Stub(McpCatalogService) {
            listToolRules() >> [new ToolInvocationRule('java-library-docs', 'desc', ['spring'], 'resolve-library-id -> query-docs', 100)]
        }
        def controller = new ToolsController(libraryTools, migration, catalog)

        when:
        def resolved = controller.resolveLibraryId('spring security csrf', 'spring security', 'csrf', 3)
        def docs = controller.queryDocs('/spring-projects/spring-security', 'csrf', 2000, 3, '4.0.0', 'HYBRID', 0.65d)
        def migrationResponse = controller.migrationAssistant(new MigrationAssistantRequest(
                "plugins { id 'org.springframework.boot' version '3.3.2' }",
                'build.gradle',
                'class Demo {}',
                25,
                '4.0.0',
                true
        ))
        def rules = controller.autoRules()

        then:
        resolved.count() == 1
        docs.count() == 1
        docs.strategy() == 'hybrid-rerank-dedup'
        migrationResponse.issueCount() == 1
        migrationResponse.findings().first().code() == 'java-version-upgrade-required'
        rules*.id() == ['java-library-docs']
    }

    def 'should default to hybrid mode on invalid mode input'() {
        given:
        def libraryTools = Stub(LibraryToolsService) {
            queryDocs(_, _, _, _, _, SearchMode.HYBRID, _) >> new LibraryDocsResponse(
                    '/spring-projects/spring-security',
                    'Spring Security',
                    'csrf',
                    'hybrid-rerank-dedup',
                    0.65f,
                    0,
                    0,
                    '',
                    []
            )
        }
        def controller = new ToolsController(libraryTools, Stub(MigrationAssistantService), Stub(McpCatalogService))

        when:
        def docs = controller.queryDocs('/spring-projects/spring-security', 'csrf', 1000, 5, null, 'not-a-mode', null)

        then:
        docs.count() == 0
    }
}
