package com.example.javamcp.tools

import com.example.javamcp.analysis.RuleEngineService
import com.example.javamcp.model.AnalyzeResponse
import com.example.javamcp.model.LibraryDoc
import com.example.javamcp.model.LibraryDocsResponse
import com.example.javamcp.model.MigrationAssistantRequest
import com.example.javamcp.model.RuleIssue
import com.example.javamcp.search.SearchMode
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

class MigrationAssistantServiceSpec extends Specification {

    def 'should detect migration gaps from gradle build and code snippet'() {
        given:
        def rules = Stub(RuleEngineService) {
            analyze(_, _) >> new AnalyzeResponse(
                    'Snippet.java',
                    1,
                    [new RuleIssue('avoid-field-injection', 4, 'LOW', 'Use constructor injection', 'Convert to constructor injection')]
            )
        }
        def libraries = Stub(LibraryToolsService) {
            queryDocs(_, _, _, _, _, _, _) >> { String libraryId, String query, Integer tokens, Integer limit, String version, SearchMode mode, Double alpha ->
                new LibraryDocsResponse(
                        libraryId,
                        'Docs',
                        query,
                        'hybrid-rerank-dedup',
                        alpha == null ? 0.65f : alpha.floatValue(),
                        1,
                        120,
                        '# Docs',
                        [new LibraryDoc(
                                libraryId + '-doc',
                                'Reference',
                                'ref excerpt',
                                'Source',
                                'https://example.com/' + libraryId.replace('/', '_'),
                                version == null ? '4.0.0' : version,
                                0.9f,
                                0.8f,
                                0.9f,
                                ['migration']
                        )]
                )
            }
        }

        def service = new MigrationAssistantService(rules, libraries, new SimpleMeterRegistry())
        def request = new MigrationAssistantRequest(
                """plugins {
  id 'org.springframework.boot' version '3.3.2'
}
java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(17)
  }
}
""",
                'build.gradle',
                'import javax.servlet.*; class Demo { void run(){} }',
                25,
                '4.0.0',
                true
        )

        when:
        def response = service.assess(request)
        def findingCodes = response.findings()*.code()

        then:
        response.buildTool() == 'GRADLE_GROOVY'
        response.detectedJavaVersion() == '17'
        response.detectedSpringBootVersion() == '3.3.2'
        response.issueCount() >= 4
        findingCodes.contains('java-version-upgrade-required')
        findingCodes.contains('spring-boot-major-upgrade-required')
        findingCodes.contains('jakarta-namespace-migration')
        findingCodes.contains('rule-avoid-field-injection')
        !response.references().isEmpty()
    }

    def 'should support code-only requests and disable reference enrichment when requested'() {
        given:
        def rules = Stub(RuleEngineService) {
            analyze(_, _) >> new AnalyzeResponse('Snippet.java', 0, [])
        }
        def libraries = Stub(LibraryToolsService)
        def service = new MigrationAssistantService(rules, libraries, new SimpleMeterRegistry())

        when:
        def response = service.assess(new MigrationAssistantRequest(
                null,
                null,
                'class Demo {}',
                null,
                null,
                false
        ))
        def findingCodes = response.findings()*.code()

        then:
        response.buildTool() == 'UNKNOWN'
        findingCodes.contains('java-version-unknown')
        findingCodes.contains('spring-boot-version-unknown')
        response.references().isEmpty()
    }

    def 'should reject empty migration requests'() {
        given:
        def service = new MigrationAssistantService(Stub(RuleEngineService), Stub(LibraryToolsService), new SimpleMeterRegistry())

        when:
        service.assess(new MigrationAssistantRequest(null, null, null, null, null, null))

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Either buildFile or code must be provided'
    }
}
