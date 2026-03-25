package com.example.javamcp.graphql;

import com.example.javamcp.analysis.RuleDefinition;
import com.example.javamcp.analysis.RuleEngineService;
import com.example.javamcp.analysis.SymbolGraphService;
import com.example.javamcp.analysis.AstService;
import com.example.javamcp.model.AnalyzeResponse;
import com.example.javamcp.model.AstResponse;
import com.example.javamcp.model.IndexStatsResponse;
import com.example.javamcp.model.McpResourceDescriptor;
import com.example.javamcp.model.McpManifest;
import com.example.javamcp.model.McpResourceResponse;
import com.example.javamcp.model.LibraryDocsResponse;
import com.example.javamcp.model.PromptTemplate;
import com.example.javamcp.model.ResolveLibraryResponse;
import com.example.javamcp.model.SymbolGraphResponse;
import com.example.javamcp.model.SearchResponse;
import com.example.javamcp.model.ToolDescriptor;
import com.example.javamcp.model.ToolInvocationRule;
import com.example.javamcp.search.IndexLifecycleService;
import com.example.javamcp.search.LuceneSearchService;
import com.example.javamcp.search.SearchMode;
import com.example.javamcp.search.SearchQuery;
import com.example.javamcp.tools.LibraryToolsService;
import com.example.javamcp.tools.McpCatalogService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class McpGraphQlController {

    private final LuceneSearchService luceneSearchService;
    private final RuleEngineService ruleEngineService;
    private final AstService astService;
    private final SymbolGraphService symbolGraphService;
    private final IndexLifecycleService indexLifecycleService;
    private final LibraryToolsService libraryToolsService;
    private final McpCatalogService mcpCatalogService;

    public McpGraphQlController(LuceneSearchService luceneSearchService,
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

    @QueryMapping
    public SearchResponse search(@Argument String q,
                                 @Argument Integer limit,
                                 @Argument String version,
                                 @Argument List<String> tags,
                                 @Argument String source,
                                 @Argument SearchMode mode,
                                 @Argument Boolean diagnostics) {
        SearchQuery query = new SearchQuery(
                q,
                limit,
                version,
                tags,
                source,
                mode,
                Boolean.TRUE.equals(diagnostics)
        );
        return luceneSearchService.search(query);
    }

    @QueryMapping
    public List<RuleDefinition> rules() {
        return ruleEngineService.listRules();
    }

    @QueryMapping
    public IndexStatsResponse indexStats() {
        return indexLifecycleService.currentStats();
    }

    @QueryMapping
    public ResolveLibraryResponse resolveLibraryId(@Argument String query,
                                                   @Argument String libraryName,
                                                   @Argument String topic,
                                                   @Argument Integer limit) {
        return libraryToolsService.resolveLibraryId(query, libraryName, topic, limit);
    }

    @QueryMapping
    public LibraryDocsResponse queryDocs(@Argument String libraryId,
                                         @Argument String query,
                                         @Argument Integer tokens,
                                         @Argument Integer limit,
                                         @Argument String version,
                                         @Argument SearchMode mode,
                                         @Argument Float alpha) {
        return libraryToolsService.queryDocs(libraryId, query, tokens, limit, version, mode, alpha == null ? null : alpha.doubleValue());
    }

    @QueryMapping
    public LibraryDocsResponse getLibraryDocs(@Argument String libraryId,
                                              @Argument String topic,
                                              @Argument Integer tokens,
                                              @Argument Integer limit,
                                              @Argument String version,
                                              @Argument SearchMode mode,
                                              @Argument Float alpha) {
        return libraryToolsService.queryDocs(libraryId, topic, tokens, limit, version, mode, alpha == null ? null : alpha.doubleValue());
    }

    @QueryMapping
    public List<ToolInvocationRule> autoRules() {
        return mcpCatalogService.listToolRules();
    }

    @QueryMapping
    public List<ToolDescriptor> mcpTools() {
        return mcpCatalogService.listTools();
    }

    @QueryMapping
    public McpManifest mcpManifest() {
        return mcpCatalogService.manifest();
    }

    @QueryMapping
    public List<McpResourceDescriptor> mcpResources() {
        return mcpCatalogService.listResources();
    }

    @QueryMapping
    public McpResourceResponse mcpResource(@Argument String resourceId) {
        return mcpCatalogService.getResource(resourceId);
    }

    @QueryMapping
    public List<PromptTemplate> prompts() {
        return mcpCatalogService.listPrompts();
    }

    @MutationMapping
    public AnalyzeResponse analyze(@Argument String fileName, @Argument String code) {
        return ruleEngineService.analyze(fileName, code);
    }

    @MutationMapping
    public AstResponse ast(@Argument String code) {
        return astService.parse(code);
    }

    @MutationMapping
    public SymbolGraphResponse symbols(@Argument String code) {
        return symbolGraphService.extract(code);
    }

    @MutationMapping
    public IndexStatsResponse rebuildIndex() {
        return indexLifecycleService.rebuildIndex();
    }
}
