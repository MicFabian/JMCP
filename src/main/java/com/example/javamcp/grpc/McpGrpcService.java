package com.example.javamcp.grpc;

import com.example.javamcp.analysis.AstService;
import com.example.javamcp.analysis.RuleDefinition;
import com.example.javamcp.analysis.RuleEngineService;
import com.example.javamcp.analysis.SymbolGraphService;
import com.example.javamcp.grpc.generated.AnalyzeReply;
import com.example.javamcp.grpc.generated.AnalyzeRequest;
import com.example.javamcp.grpc.generated.AstClass;
import com.example.javamcp.grpc.generated.AstMethod;
import com.example.javamcp.grpc.generated.AstReply;
import com.example.javamcp.grpc.generated.GetLibraryDocsReply;
import com.example.javamcp.grpc.generated.GetLibraryDocsRequest;
import com.example.javamcp.grpc.generated.IndexStatsReply;
import com.example.javamcp.grpc.generated.IndexStatsRequest;
import com.example.javamcp.grpc.generated.LibraryCandidate;
import com.example.javamcp.grpc.generated.LibraryDoc;
import com.example.javamcp.grpc.generated.McpManifestReply;
import com.example.javamcp.grpc.generated.McpManifestRequest;
import com.example.javamcp.grpc.generated.McpPrompt;
import com.example.javamcp.grpc.generated.McpResource;
import com.example.javamcp.grpc.generated.McpServiceGrpc;
import com.example.javamcp.grpc.generated.McpTool;
import com.example.javamcp.grpc.generated.McpToolRule;
import com.example.javamcp.grpc.generated.RebuildIndexRequest;
import com.example.javamcp.grpc.generated.ResolveLibraryIdReply;
import com.example.javamcp.grpc.generated.ResolveLibraryIdRequest;
import com.example.javamcp.grpc.generated.RuleIssue;
import com.example.javamcp.grpc.generated.RulesReply;
import com.example.javamcp.grpc.generated.RulesRequest;
import com.example.javamcp.grpc.generated.SearchDiagnostics;
import com.example.javamcp.grpc.generated.SearchReply;
import com.example.javamcp.grpc.generated.SearchRequest;
import com.example.javamcp.grpc.generated.SearchResult;
import com.example.javamcp.grpc.generated.SymbolEdge;
import com.example.javamcp.grpc.generated.SymbolNode;
import com.example.javamcp.grpc.generated.SymbolsReply;
import com.example.javamcp.grpc.generated.SymbolsRequest;
import com.example.javamcp.model.IndexStatsResponse;
import com.example.javamcp.model.LibraryDocsResponse;
import com.example.javamcp.model.ResolveLibraryResponse;
import com.example.javamcp.search.IndexLifecycleService;
import com.example.javamcp.search.LuceneSearchService;
import com.example.javamcp.search.SearchMode;
import com.example.javamcp.search.SearchQuery;
import com.example.javamcp.tools.LibraryToolsService;
import com.example.javamcp.tools.McpCatalogService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Locale;

@GrpcService
public class McpGrpcService extends McpServiceGrpc.McpServiceImplBase {

    private final LuceneSearchService luceneSearchService;
    private final RuleEngineService ruleEngineService;
    private final AstService astService;
    private final SymbolGraphService symbolGraphService;
    private final IndexLifecycleService indexLifecycleService;
    private final LibraryToolsService libraryToolsService;
    private final McpCatalogService mcpCatalogService;

    public McpGrpcService(LuceneSearchService luceneSearchService,
                          RuleEngineService ruleEngineService,
                          AstService astService,
                          SymbolGraphService symbolGraphService,
                          IndexLifecycleService indexLifecycleService,
                          LibraryToolsService libraryToolsService,
                          McpCatalogService mcpCatalogService) {
        this.luceneSearchService = luceneSearchService;
        this.ruleEngineService = ruleEngineService;
        this.astService = astService;
        this.symbolGraphService = symbolGraphService;
        this.indexLifecycleService = indexLifecycleService;
        this.libraryToolsService = libraryToolsService;
        this.mcpCatalogService = mcpCatalogService;
    }

    @Override
    public void search(SearchRequest request, StreamObserver<SearchReply> responseObserver) {
        try {
            SearchQuery query = new SearchQuery(
                    request.getQuery(),
                    request.getLimit(),
                    emptyToNull(request.getVersion()),
                    request.getTagsList(),
                    emptyToNull(request.getSource()),
                    parseMode(request.getMode()),
                    request.getDiagnostics()
            );

            com.example.javamcp.model.SearchResponse response = luceneSearchService.search(query);
            SearchReply.Builder builder = SearchReply.newBuilder()
                    .setQuery(defaultString(response.query()))
                    .setCount(response.count());

            for (com.example.javamcp.model.SearchResult result : response.results()) {
                builder.addResults(SearchResult.newBuilder()
                        .setId(defaultString(result.id()))
                        .setTitle(defaultString(result.title()))
                        .setSnippet(defaultString(result.snippet()))
                        .setSource(defaultString(result.source()))
                        .setSourceUrl(defaultString(result.sourceUrl()))
                        .setVersion(defaultString(result.version()))
                        .setScore(result.score())
                        .build());
            }

            if (response.diagnostics() != null) {
                builder.setDiagnostics(SearchDiagnostics.newBuilder()
                        .setMode(defaultString(response.diagnostics().mode()))
                        .setExpandedQuery(defaultString(response.diagnostics().expandedQuery()))
                        .setLexicalCandidates(response.diagnostics().lexicalCandidates())
                        .setVectorCandidates(response.diagnostics().vectorCandidates())
                        .setElapsedMillis(response.diagnostics().elapsedMillis())
                        .build());
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(defaultString(e.getMessage())).withCause(e).asRuntimeException());
        }
    }

    @Override
    public void getMcpManifest(McpManifestRequest request, StreamObserver<McpManifestReply> responseObserver) {
        try {
            com.example.javamcp.model.McpManifest manifest = mcpCatalogService.manifest();
            McpManifestReply.Builder builder = McpManifestReply.newBuilder()
                    .setServerName(defaultString(manifest.serverName()))
                    .setVersion(defaultString(manifest.version()))
                    .setGeneratedAt(defaultString(manifest.generatedAt()));

            manifest.tools().forEach(tool -> builder.addTools(McpTool.newBuilder()
                    .setName(defaultString(tool.name()))
                    .setDescription(defaultString(tool.description()))
                    .setInputSchemaHint(defaultString(tool.inputSchemaHint()))
                    .build()));

            manifest.toolRules().forEach(rule -> builder.addToolRules(McpToolRule.newBuilder()
                    .setId(defaultString(rule.id()))
                    .setDescription(defaultString(rule.description()))
                    .addAllTriggerPatterns(rule.triggerPatterns())
                    .setToolName(defaultString(rule.toolName()))
                    .setPriority(rule.priority())
                    .build()));

            manifest.resources().forEach(resource -> builder.addResources(McpResource.newBuilder()
                    .setResourceId(defaultString(resource.resourceId()))
                    .setUri(defaultString(resource.uri()))
                    .setTitle(defaultString(resource.title()))
                    .setVersion(defaultString(resource.version()))
                    .addAllTags(resource.tags())
                    .setSource(defaultString(resource.source()))
                    .setSourceUrl(defaultString(resource.sourceUrl()))
                    .build()));

            manifest.prompts().forEach(prompt -> builder.addPrompts(McpPrompt.newBuilder()
                    .setId(defaultString(prompt.id()))
                    .setName(defaultString(prompt.name()))
                    .setDescription(defaultString(prompt.description()))
                    .setTemplate(defaultString(prompt.template()))
                    .build()));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(defaultString(e.getMessage())).withCause(e).asRuntimeException());
        }
    }

    @Override
    public void resolveLibraryId(ResolveLibraryIdRequest request, StreamObserver<ResolveLibraryIdReply> responseObserver) {
        try {
            ResolveLibraryResponse response = libraryToolsService.resolveLibraryId(
                    emptyToNull(request.getQuery()),
                    emptyToNull(request.getLibraryName()),
                    emptyToNull(request.getTopic()),
                    request.getLimit()
            );

            ResolveLibraryIdReply.Builder builder = ResolveLibraryIdReply.newBuilder()
                    .setQuery(defaultString(response.query()))
                    .setCount(response.count());

            response.libraries().forEach(library -> builder.addLibraries(LibraryCandidate.newBuilder()
                    .setLibraryId(defaultString(library.libraryId()))
                    .setName(defaultString(library.name()))
                    .setSummary(defaultString(library.summary()))
                    .setDocumentCount(library.documentCount())
                    .addAllVersions(library.versions())
                    .addAllSources(library.sources())
                    .setScore(library.score())
                    .build()));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(defaultString(e.getMessage())).withCause(e).asRuntimeException());
        }
    }

    @Override
    public void getLibraryDocs(GetLibraryDocsRequest request, StreamObserver<GetLibraryDocsReply> responseObserver) {
        if (request.getLibraryId().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("libraryId must not be blank").asRuntimeException());
            return;
        }

        try {
            String effectiveQuery = emptyToNull(request.getQuery()) == null
                    ? emptyToNull(request.getTopic())
                    : request.getQuery();

            LibraryDocsResponse response = libraryToolsService.queryDocs(
                    request.getLibraryId(),
                    effectiveQuery,
                    request.getTokens(),
                    request.getLimit(),
                    emptyToNull(request.getVersion()),
                    parseMode(request.getMode()),
                    request.hasAlpha() ? (double) request.getAlpha() : null
            );

            GetLibraryDocsReply.Builder builder = GetLibraryDocsReply.newBuilder()
                    .setLibraryId(defaultString(response.libraryId()))
                    .setLibraryName(defaultString(response.libraryName()))
                    .setTopic(defaultString(response.topic()))
                    .setCount(response.count())
                    .setApproxTokens(response.approxTokens())
                    .setContext(defaultString(response.context()))
                    .setStrategy(defaultString(response.strategy()))
                    .setAlpha(response.alpha());

            response.documents().forEach(doc -> builder.addDocuments(LibraryDoc.newBuilder()
                    .setId(defaultString(doc.id()))
                    .setTitle(defaultString(doc.title()))
                    .setExcerpt(defaultString(doc.excerpt()))
                    .setSource(defaultString(doc.source()))
                    .setSourceUrl(defaultString(doc.sourceUrl()))
                    .setVersion(defaultString(doc.version()))
                    .setScore(doc.score())
                    .setRetrievalScore(doc.retrievalScore())
                    .setRerankScore(doc.rerankScore())
                    .addAllMatchedTerms(doc.matchedTerms())
                    .build()));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(defaultString(e.getMessage())).withCause(e).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(defaultString(e.getMessage())).withCause(e).asRuntimeException());
        }
    }

    @Override
    public void queryDocs(GetLibraryDocsRequest request, StreamObserver<GetLibraryDocsReply> responseObserver) {
        getLibraryDocs(request, responseObserver);
    }

    @Override
    public void analyze(AnalyzeRequest request, StreamObserver<AnalyzeReply> responseObserver) {
        if (request.getCode().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("code must not be blank").asRuntimeException());
            return;
        }

        try {
            com.example.javamcp.model.AnalyzeResponse response = ruleEngineService.analyze(
                    emptyToNull(request.getFileName()),
                    request.getCode()
            );

            AnalyzeReply.Builder builder = AnalyzeReply.newBuilder()
                    .setFile(defaultString(response.file()))
                    .setIssueCount(response.issueCount());

            for (com.example.javamcp.model.RuleIssue issue : response.issues()) {
                builder.addIssues(RuleIssue.newBuilder()
                        .setRule(defaultString(issue.rule()))
                        .setLine(issue.line())
                        .setSeverity(defaultString(issue.severity()))
                        .setMessage(defaultString(issue.message()))
                        .setSuggestion(defaultString(issue.suggestion()))
                        .build());
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(defaultString(e.getMessage())).withCause(e).asRuntimeException());
        }
    }

    @Override
    public void ast(com.example.javamcp.grpc.generated.AstRequest request,
                    StreamObserver<AstReply> responseObserver) {
        if (request.getCode().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("code must not be blank").asRuntimeException());
            return;
        }

        try {
            com.example.javamcp.model.AstResponse response = astService.parse(request.getCode());
            AstReply.Builder builder = AstReply.newBuilder().setClassCount(response.classCount());

            for (com.example.javamcp.model.AstClass astClass : response.classes()) {
                AstClass.Builder classBuilder = AstClass.newBuilder()
                        .setName(defaultString(astClass.name()))
                        .setPackageName(defaultString(astClass.packageName()));

                for (com.example.javamcp.model.AstMethod method : astClass.methods()) {
                    classBuilder.addMethods(AstMethod.newBuilder()
                            .setName(defaultString(method.name()))
                            .setSignature(defaultString(method.signature()))
                            .setReturnType(defaultString(method.returnType()))
                            .setJavadoc(defaultString(method.javadoc()))
                            .build());
                }
                builder.addClasses(classBuilder.build());
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(defaultString(e.getMessage())).withCause(e).asRuntimeException());
        }
    }

    @Override
    public void symbols(SymbolsRequest request, StreamObserver<SymbolsReply> responseObserver) {
        if (request.getCode().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("code must not be blank").asRuntimeException());
            return;
        }

        try {
            com.example.javamcp.model.SymbolGraphResponse response = symbolGraphService.extract(request.getCode());
            SymbolsReply.Builder builder = SymbolsReply.newBuilder()
                    .setNodeCount(response.nodeCount())
                    .setEdgeCount(response.edgeCount());

            response.nodes().forEach(node -> builder.addNodes(SymbolNode.newBuilder()
                    .setId(defaultString(node.id()))
                    .setType(defaultString(node.type()))
                    .setName(defaultString(node.name()))
                    .setQualifiedName(defaultString(node.qualifiedName()))
                    .build()));

            response.edges().forEach(edge -> builder.addEdges(SymbolEdge.newBuilder()
                    .setFrom(defaultString(edge.from()))
                    .setTo(defaultString(edge.to()))
                    .setRelation(defaultString(edge.relation()))
                    .build()));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(defaultString(e.getMessage())).withCause(e).asRuntimeException());
        }
    }

    @Override
    public void listRules(RulesRequest request, StreamObserver<RulesReply> responseObserver) {
        try {
            RulesReply.Builder builder = RulesReply.newBuilder();
            for (RuleDefinition rule : ruleEngineService.listRules()) {
                builder.addRules(com.example.javamcp.grpc.generated.RuleDefinition.newBuilder()
                        .setId(defaultString(rule.id()))
                        .setDescription(defaultString(rule.description()))
                        .setMatchType(rule.matchType().name())
                        .setPattern(defaultString(rule.pattern()))
                        .setFix(defaultString(rule.fix()))
                        .setSeverity(defaultString(rule.severity()))
                        .setTarget(defaultString(rule.target()))
                        .setEnabled(Boolean.TRUE.equals(rule.enabled()))
                        .build());
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(defaultString(e.getMessage())).withCause(e).asRuntimeException());
        }
    }

    @Override
    public void indexStats(IndexStatsRequest request, StreamObserver<IndexStatsReply> responseObserver) {
        try {
            responseObserver.onNext(toIndexStats(indexLifecycleService.currentStats()));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(defaultString(e.getMessage())).withCause(e).asRuntimeException());
        }
    }

    @Override
    public void rebuildIndex(RebuildIndexRequest request, StreamObserver<IndexStatsReply> responseObserver) {
        try {
            responseObserver.onNext(toIndexStats(indexLifecycleService.rebuildIndex()));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(defaultString(e.getMessage())).withCause(e).asRuntimeException());
        }
    }

    private IndexStatsReply toIndexStats(IndexStatsResponse response) {
        return IndexStatsReply.newBuilder()
                .setDocumentCount(response.documentCount())
                .addAllVersions(response.versions())
                .addAllTags(response.tags())
                .addAllSources(response.sources())
                .setLastIndexedAt(defaultString(response.lastIndexedAt()))
                .build();
    }

    private SearchMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return SearchMode.HYBRID;
        }

        try {
            return SearchMode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SearchMode.HYBRID;
        }
    }

    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
