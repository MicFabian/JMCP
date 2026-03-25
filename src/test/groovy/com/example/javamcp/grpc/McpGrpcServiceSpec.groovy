package com.example.javamcp.grpc

import com.example.javamcp.analysis.AstService
import com.example.javamcp.analysis.RuleEngineService
import com.example.javamcp.analysis.SymbolGraphService
import com.example.javamcp.grpc.generated.GetLibraryDocsReply
import com.example.javamcp.grpc.generated.GetLibraryDocsRequest
import com.example.javamcp.grpc.generated.McpManifestReply
import com.example.javamcp.grpc.generated.McpManifestRequest
import com.example.javamcp.grpc.generated.ResolveLibraryIdReply
import com.example.javamcp.grpc.generated.ResolveLibraryIdRequest
import com.example.javamcp.grpc.generated.SearchReply
import com.example.javamcp.grpc.generated.SearchRequest
import com.example.javamcp.model.IndexStatsResponse
import com.example.javamcp.model.LibraryCandidate
import com.example.javamcp.model.LibraryDoc
import com.example.javamcp.model.LibraryDocsResponse
import com.example.javamcp.model.McpManifest
import com.example.javamcp.model.McpResourceDescriptor
import com.example.javamcp.model.PromptTemplate
import com.example.javamcp.model.ResolveLibraryResponse
import com.example.javamcp.model.SearchResponse
import com.example.javamcp.model.ToolDescriptor
import com.example.javamcp.model.ToolInvocationRule
import com.example.javamcp.search.IndexLifecycleService
import com.example.javamcp.search.LuceneSearchService
import com.example.javamcp.tools.LibraryToolsService
import com.example.javamcp.tools.McpCatalogService
import io.grpc.stub.StreamObserver
import spock.lang.Specification

class McpGrpcServiceSpec extends Specification {

    def 'should map search response to grpc payload'() {
        given:
        def lucene = Stub(LuceneSearchService) {
            search(_) >> new SearchResponse('constructor', 0, [], null)
        }
        def ruleEngine = Stub(RuleEngineService)
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
            manifest() >> new McpManifest(
                    'java-mcp',
                    'v0.1.0',
                    '2026-02-26T00:00:00Z',
                    [new ToolDescriptor('query-docs', 'desc', '{}')],
                    [new ToolInvocationRule('java-library-docs', 'desc', ['spring'], 'resolve-library-id -> query-docs', 100)],
                    [new McpResourceDescriptor('spring-boot-csrf', 'mcp://docs/spring-boot-csrf', 'Enable CSRF Protection', '4.0.0', ['security'], 'Spring Security Reference', 'https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html')],
                    [new PromptTemplate('resolve-then-query', 'Resolve Then Query', 'desc', 'template')]
            )
        }

        def service = new McpGrpcService(lucene, ruleEngine, ast, symbols, lifecycle, tools, catalog)
        def holder = new SearchReply[1]
        def manifestHolder = new McpManifestReply[1]
        def resolveHolder = new ResolveLibraryIdReply[1]
        def docsHolder = new GetLibraryDocsReply[1]
        def observer = new StreamObserver<SearchReply>() {
            @Override
            void onNext(SearchReply value) { holder[0] = value }

            @Override
            void onError(Throwable t) { throw t }

            @Override
            void onCompleted() {}
        }
        def resolveObserver = new StreamObserver<ResolveLibraryIdReply>() {
            @Override
            void onNext(ResolveLibraryIdReply value) { resolveHolder[0] = value }

            @Override
            void onError(Throwable t) { throw t }

            @Override
            void onCompleted() {}
        }
        def docsObserver = new StreamObserver<GetLibraryDocsReply>() {
            @Override
            void onNext(GetLibraryDocsReply value) { docsHolder[0] = value }

            @Override
            void onError(Throwable t) { throw t }

            @Override
            void onCompleted() {}
        }
        def manifestObserver = new StreamObserver<McpManifestReply>() {
            @Override
            void onNext(McpManifestReply value) { manifestHolder[0] = value }

            @Override
            void onError(Throwable t) { throw t }

            @Override
            void onCompleted() {}
        }

        when:
        service.search(SearchRequest.newBuilder().setQuery('constructor').setLimit(5).build(), observer)
        service.getMcpManifest(McpManifestRequest.newBuilder().build(), manifestObserver)
        service.resolveLibraryId(ResolveLibraryIdRequest.newBuilder().setQuery('spring security csrf').setLibraryName('spring security').setTopic('csrf').setLimit(3).build(), resolveObserver)
        service.queryDocs(GetLibraryDocsRequest.newBuilder().setLibraryId('/spring-projects/spring-security').setQuery('csrf').setTokens(1500).setLimit(3).setMode('HYBRID').setAlpha(0.65f).build(), docsObserver)

        then:
        holder[0] != null
        holder[0].getQuery() == 'constructor'
        holder[0].getCount() == 0
        manifestHolder[0] != null
        manifestHolder[0].getServerName() == 'java-mcp'
        manifestHolder[0].getToolsCount() == 1
        resolveHolder[0] != null
        resolveHolder[0].getCount() == 1
        docsHolder[0] != null
        docsHolder[0].getCount() == 1
        docsHolder[0].getStrategy() == 'hybrid-rerank-dedup'
        docsHolder[0].getDocuments(0).getMatchedTermsList() == ['csrf']
    }
}
