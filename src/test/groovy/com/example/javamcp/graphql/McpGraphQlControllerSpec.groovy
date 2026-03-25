package com.example.javamcp.graphql

import com.example.javamcp.analysis.AstService
import com.example.javamcp.analysis.RuleDefinition
import com.example.javamcp.analysis.RuleEngineService
import com.example.javamcp.analysis.RuleMatchType
import com.example.javamcp.analysis.SymbolGraphService
import com.example.javamcp.model.IndexStatsResponse
import com.example.javamcp.model.LibraryCandidate
import com.example.javamcp.model.LibraryDoc
import com.example.javamcp.model.LibraryDocsResponse
import com.example.javamcp.model.McpManifest
import com.example.javamcp.model.McpResourceDescriptor
import com.example.javamcp.model.McpResourceResponse
import com.example.javamcp.model.PromptTemplate
import com.example.javamcp.model.ResolveLibraryResponse
import com.example.javamcp.model.SearchResponse
import com.example.javamcp.model.ToolDescriptor
import com.example.javamcp.model.ToolInvocationRule
import com.example.javamcp.search.IndexLifecycleService
import com.example.javamcp.search.LuceneSearchService
import com.example.javamcp.search.SearchMode
import com.example.javamcp.tools.LibraryToolsService
import com.example.javamcp.tools.McpCatalogService
import spock.lang.Specification

class McpGraphQlControllerSpec extends Specification {

    def 'should delegate search, tool, and catalog queries'() {
        given:
        def lucene = Stub(LuceneSearchService) {
            search(_) >> new SearchResponse('q', 0, [], null)
        }
        def rules = Stub(RuleEngineService) {
            listRules() >> [new RuleDefinition('r1', 'desc', RuleMatchType.CONTAINS, 'x', 'fix', 'LOW', null, true)]
        }
        def ast = Stub(AstService)
        def symbols = Stub(SymbolGraphService)
        def lifecycle = Stub(IndexLifecycleService) {
            currentStats() >> new IndexStatsResponse(0, [], [], [], null)
        }
        def tools = Stub(LibraryToolsService) {
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
        def catalog = Stub(McpCatalogService) {
            listToolRules() >> [new ToolInvocationRule('r1', 'desc', ['spring'], 'resolve-library-id -> query-docs', 100)]
            listTools() >> [new ToolDescriptor('query-docs', 'desc', '{}')]
            manifest() >> new McpManifest(
                    'java-mcp',
                    'v0.1.0',
                    '2026-02-26T00:00:00Z',
                    [new ToolDescriptor('query-docs', 'desc', '{}')],
                    [new ToolInvocationRule('r1', 'desc', ['spring'], 'resolve-library-id -> query-docs', 100)],
                    [new McpResourceDescriptor('spring-boot-csrf', 'mcp://docs/spring-boot-csrf', 'Enable CSRF Protection', '4.0.0', ['security'], 'Spring Security Reference', 'https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html')],
                    [new PromptTemplate('resolve-then-query', 'Resolve Then Query', 'desc', 'template')]
            )
            listResources() >> [new McpResourceDescriptor('spring-boot-csrf', 'mcp://docs/spring-boot-csrf', 'Enable CSRF Protection', '4.0.0', ['security'], 'Spring Security Reference', 'https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html')]
            getResource(_) >> new McpResourceResponse(
                    new McpResourceDescriptor('spring-boot-csrf', 'mcp://docs/spring-boot-csrf', 'Enable CSRF Protection', '4.0.0', ['security'], 'Spring Security Reference', 'https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html'),
                    'By default, Spring Security enables CSRF protection.'
            )
            listPrompts() >> [new PromptTemplate('resolve-then-query', 'Resolve Then Query', 'desc', 'template')]
        }

        def controller = new McpGraphQlController(lucene, rules, ast, symbols, lifecycle, tools, catalog)

        when:
        def searchResponse = controller.search('abc', 5, null, [], null, SearchMode.HYBRID, true)
        def ruleResponse = controller.rules()
        def resolved = controller.resolveLibraryId('spring security csrf', 'spring security', 'csrf', 3)
        def docs = controller.queryDocs('/spring-projects/spring-security', 'csrf', 1500, 3, '4.0.0', SearchMode.HYBRID, 0.65f)
        def autoRules = controller.autoRules()
        def mcpTools = controller.mcpTools()
        def manifest = controller.mcpManifest()
        def resources = controller.mcpResources()
        def resource = controller.mcpResource('spring-boot-csrf')
        def prompts = controller.prompts()

        then:
        searchResponse.count() == 0
        ruleResponse*.id() == ['r1']
        resolved.count() == 1
        docs.count() == 1
        autoRules.size() == 1
        mcpTools.size() == 1
        manifest.serverName() == 'java-mcp'
        resources.size() == 1
        resource.resource().resourceId() == 'spring-boot-csrf'
        prompts.size() == 1
    }
}
