package com.example.javamcp.mcp;

import com.example.javamcp.analysis.AstService;
import com.example.javamcp.analysis.RuleEngineService;
import com.example.javamcp.analysis.SymbolGraphService;
import com.example.javamcp.model.JavaDocsToolResponse;
import com.example.javamcp.model.LibraryDocsResponse;
import com.example.javamcp.model.MigrationAssistantRequest;
import com.example.javamcp.model.McpResourceDescriptor;
import com.example.javamcp.model.McpResourceResponse;
import com.example.javamcp.model.PromptTemplate;
import com.example.javamcp.model.ResolveLibraryResponse;
import com.example.javamcp.model.SearchResponse;
import com.example.javamcp.model.SearchResult;
import com.example.javamcp.search.IndexLifecycleService;
import com.example.javamcp.search.LuceneSearchService;
import com.example.javamcp.search.SearchMode;
import com.example.javamcp.search.SearchModeResolver;
import com.example.javamcp.search.SearchQuery;
import com.example.javamcp.tools.LibraryToolsService;
import com.example.javamcp.tools.MigrationAssistantService;
import com.example.javamcp.tools.McpCatalogService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class NativeMcpSpecificationFactory {

    private static final String DEFAULT_RESOURCE_MIME_TYPE = "text/markdown";

    private final ObjectMapper objectMapper;
    private final LuceneSearchService luceneSearchService;
    private final LibraryToolsService libraryToolsService;
    private final RuleEngineService ruleEngineService;
    private final AstService astService;
    private final SymbolGraphService symbolGraphService;
    private final MigrationAssistantService migrationAssistantService;
    private final IndexLifecycleService indexLifecycleService;
    private final McpCatalogService mcpCatalogService;
    private final SearchModeResolver searchModeResolver;

    public NativeMcpSpecificationFactory(ObjectMapper objectMapper,
                                         LuceneSearchService luceneSearchService,
                                         LibraryToolsService libraryToolsService,
                                         RuleEngineService ruleEngineService,
                                         AstService astService,
                                         SymbolGraphService symbolGraphService,
                                         MigrationAssistantService migrationAssistantService,
                                         IndexLifecycleService indexLifecycleService,
                                         McpCatalogService mcpCatalogService,
                                         SearchModeResolver searchModeResolver) {
        this.objectMapper = objectMapper;
        this.luceneSearchService = luceneSearchService;
        this.libraryToolsService = libraryToolsService;
        this.ruleEngineService = ruleEngineService;
        this.astService = astService;
        this.symbolGraphService = symbolGraphService;
        this.migrationAssistantService = migrationAssistantService;
        this.indexLifecycleService = indexLifecycleService;
        this.mcpCatalogService = mcpCatalogService;
        this.searchModeResolver = searchModeResolver;
    }

    public List<McpServerFeatures.SyncToolSpecification> toolSpecifications() {
        return List.of(
                javaDocsTool(),
                resolveLibraryIdTool(),
                queryDocsTool(),
                searchTool(),
                analyzeTool(),
                astTool(),
                symbolsTool(),
                migrationAssistantTool(),
                indexStatsTool(),
                rebuildIndexTool(),
                manifestTool()
        );
    }

    public List<McpServerFeatures.SyncResourceSpecification> resourceSpecifications() {
        List<McpResourceDescriptor> resources = mcpCatalogService.listResources();
        if (resources.isEmpty()) {
            return List.of();
        }

        List<McpServerFeatures.SyncResourceSpecification> specifications = new ArrayList<>();
        for (McpResourceDescriptor descriptor : resources) {
            McpSchema.Resource resource = McpSchema.Resource.builder()
                    .uri(descriptor.uri())
                    .name(descriptor.resourceId())
                    .title(blankToNull(descriptor.title()))
                    .description(resourceDescription(descriptor))
                    .mimeType(DEFAULT_RESOURCE_MIME_TYPE)
                    .build();

            specifications.add(new McpServerFeatures.SyncResourceSpecification(
                    resource,
                    (exchange, request) -> {
                        try {
                            return readResource(descriptor.resourceId());
                        } catch (Exception ex) {
                            throw new IllegalArgumentException("Failed to read resource: " + descriptor.resourceId(), ex);
                        }
                    }
            ));
        }

        return specifications;
    }

    public List<McpServerFeatures.SyncResourceTemplateSpecification> resourceTemplateSpecifications() {
        McpSchema.ResourceTemplate template = McpSchema.ResourceTemplate.builder()
                .uriTemplate("mcp://docs/{resourceId}")
                .name("doc-by-id")
                .title("Document by resource id")
                .description("Read an ingested MCP document by resource id")
                .mimeType(DEFAULT_RESOURCE_MIME_TYPE)
                .build();

        McpServerFeatures.SyncResourceTemplateSpecification specification =
                new McpServerFeatures.SyncResourceTemplateSpecification(
                        template,
                        (exchange, request) -> {
                            try {
                                return readResource(request.uri());
                            } catch (Exception ex) {
                                throw new IllegalArgumentException("Failed to read resource: " + request.uri(), ex);
                            }
                        }
                );
        return List.of(specification);
    }

    public List<McpServerFeatures.SyncPromptSpecification> promptSpecifications() {
        List<PromptTemplate> templates = mcpCatalogService.listPrompts();
        if (templates.isEmpty()) {
            return List.of();
        }

        List<McpServerFeatures.SyncPromptSpecification> specifications = new ArrayList<>();
        for (PromptTemplate template : templates) {
            McpSchema.Prompt prompt = new McpSchema.Prompt(
                    template.id(),
                    template.name(),
                    template.description(),
                    List.of(new McpSchema.PromptArgument(
                            "variablesJson",
                            "Optional JSON object used to fill {{placeholders}} in the template",
                            false
                    ))
            );

            specifications.add(new McpServerFeatures.SyncPromptSpecification(
                    prompt,
                    (exchange, request) -> {
                        String variablesJson = stringArg(request.arguments(), "variablesJson");
                        String rendered = renderTemplate(template.template(), variablesJson);
                        return new McpSchema.GetPromptResult(
                                template.description(),
                                List.of(new McpSchema.PromptMessage(
                                        McpSchema.Role.USER,
                                        new McpSchema.TextContent(rendered)
                                ))
                        );
                    }
            ));
        }
        return specifications;
    }

    private McpServerFeatures.SyncToolSpecification javaDocsTool() {
        McpSchema.Tool tool = tool(
                "java-docs",
                "Java and Spring Docs",
                "Retrieve Java, Spring, or OpenJDK documentation for usage, configuration, best-practice, or migration questions.",
                Map.of(
                        "query", stringProperty("Required natural-language question, e.g. 'spring security csrf requestMatchers'"),
                        "libraryName", stringProperty("Optional library hint, e.g. 'spring security' or 'openjdk'"),
                        "version", stringProperty("Optional exact version filter"),
                        "tokens", integerProperty("Approximate token budget for context assembly"),
                        "limit", integerProperty("Maximum number of documents"),
                        "alpha", numberProperty("Hybrid rerank weight (0..1)")
                ),
                List.of("query"),
                javaDocsOutputSchema(),
                readOnlyToolAnnotations()
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> safeToolCall(() -> {
            Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
            String query = requireNonBlank(firstNonBlank(stringArg(args, "query"), stringArg(args, "q")), "query");
            String libraryName = stringArg(args, "libraryName");
            String version = stringArg(args, "version");
            Integer tokens = integerArg(args, "tokens");
            Integer limit = integerArg(args, "limit");
            Double alpha = doubleArg(args, "alpha");

            ResolveLibraryResponse resolved = libraryToolsService.resolveLibraryId(query, libraryName, null, 3);
            Optional<com.example.javamcp.model.LibraryCandidate> bestCandidate = resolved.libraries().stream().findFirst();

            if (bestCandidate.isPresent()) {
                var candidate = bestCandidate.get();
                LibraryDocsResponse docs = libraryToolsService.queryDocs(
                        candidate.libraryId(),
                        query,
                        tokens,
                        limit,
                        version,
                        SearchMode.HYBRID,
                        alpha
                );
                return success(new JavaDocsToolResponse(
                        query,
                        libraryName,
                        docs.libraryId(),
                        docs.libraryName(),
                        "resolved-library",
                        docs.count(),
                        docs.context(),
                        docs.documents()
                ));
            }

            SearchResponse search = luceneSearchService.search(new SearchQuery(
                    query,
                    limit,
                    version,
                    List.of(),
                    null,
                    SearchMode.HYBRID,
                    false
            ));

            List<com.example.javamcp.model.LibraryDoc> documents = search.results().stream()
                    .map(this::toLibraryDoc)
                    .toList();

            return success(new JavaDocsToolResponse(
                    query,
                    libraryName,
                    null,
                    null,
                    "global-search-fallback",
                    documents.size(),
                    "",
                    documents
            ));
        }));
    }

    private McpServerFeatures.SyncToolSpecification resolveLibraryIdTool() {
        McpSchema.Tool tool = tool(
                "resolve-library-id",
                "Resolve Library ID",
                "Resolve a canonical Java, Spring, or OpenJDK library identifier before scoped documentation retrieval.",
                Map.of(
                        "query", stringProperty("Natural language query (preferred input)"),
                        "libraryName", stringProperty("Library name hint, e.g. spring security"),
                        "topic", stringProperty("Optional topic, e.g. csrf"),
                        "limit", integerProperty("Maximum number of candidates")
                ),
                List.of(),
                resolveLibraryOutputSchema(),
                readOnlyToolAnnotations()
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> safeToolCall(() -> {
            Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
            ResolveLibraryResponse response = libraryToolsService.resolveLibraryId(
                    stringArg(args, "query"),
                    stringArg(args, "libraryName"),
                    stringArg(args, "topic"),
                    integerArg(args, "limit")
            );
            return success(response);
        }));
    }

    private McpServerFeatures.SyncToolSpecification queryDocsTool() {
        McpSchema.Tool tool = tool(
                "query-docs",
                "Query Library Docs",
                "Retrieve deduplicated documentation chunks for a resolved Java, Spring, or OpenJDK library.",
                Map.of(
                        "libraryId", stringProperty("Canonical library id, e.g. /spring-projects/spring-security"),
                        "query", stringProperty("Query within that library"),
                        "tokens", integerProperty("Approximate token budget"),
                        "limit", integerProperty("Max number of documents"),
                        "version", stringProperty("Optional exact version filter"),
                        "mode", enumProperty("Retrieval mode", List.of("HYBRID", "LEXICAL", "VECTOR")),
                        "alpha", numberProperty("Rerank weight (0..1)")
                ),
                List.of("libraryId"),
                libraryDocsOutputSchema(),
                readOnlyToolAnnotations()
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> safeToolCall(() -> {
            Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
            String libraryId = requireNonBlank(stringArg(args, "libraryId"), "libraryId");

            LibraryDocsResponse response = libraryToolsService.queryDocs(
                    libraryId,
                    stringArg(args, "query"),
                    integerArg(args, "tokens"),
                    integerArg(args, "limit"),
                    stringArg(args, "version"),
                    searchModeResolver.resolve(stringArg(args, "mode")),
                    doubleArg(args, "alpha")
            );
            return success(response);
        }));
    }

    private McpServerFeatures.SyncToolSpecification searchTool() {
        McpSchema.Tool tool = tool(
                "search",
                "Search Indexed Java Content",
                "Hybrid search across all indexed Java, Spring, and OpenJDK MCP content.",
                Map.of(
                        "q", stringProperty("Search query"),
                        "limit", integerProperty("Maximum number of results"),
                        "version", stringProperty("Optional exact version filter"),
                        "tags", arrayOfStringsProperty("Tag filters; array or comma-separated string"),
                        "source", stringProperty("Optional exact source filter"),
                        "mode", enumProperty("Retrieval mode", List.of("HYBRID", "LEXICAL", "VECTOR")),
                        "diagnostics", booleanProperty("Include diagnostics in response")
                ),
                List.of(),
                searchOutputSchema(),
                readOnlyToolAnnotations()
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> safeToolCall(() -> {
            Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
            String query = firstNonBlank(stringArg(args, "q"), stringArg(args, "query"));

            SearchResponse response = luceneSearchService.search(new SearchQuery(
                    query,
                    integerArg(args, "limit"),
                    stringArg(args, "version"),
                    tagsArg(args, "tags"),
                    stringArg(args, "source"),
                    searchModeResolver.resolve(stringArg(args, "mode")),
                    booleanArg(args, "diagnostics", false)
            ));
            return success(response);
        }));
    }

    private McpServerFeatures.SyncToolSpecification analyzeTool() {
        McpSchema.Tool tool = tool(
                "analyze",
                "Analyze Java Code",
                "Review Java code for best-practice issues and return concrete fix suggestions.",
                Map.of(
                        "fileName", stringProperty("Optional file name"),
                        "code", stringProperty("Java code snippet")
                ),
                List.of("code"),
                analyzeOutputSchema(),
                readOnlyToolAnnotations()
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> safeToolCall(() -> {
            Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
            String code = requireNonBlank(stringArg(args, "code"), "code");
            return success(ruleEngineService.analyze(stringArg(args, "fileName"), code));
        }));
    }

    private McpServerFeatures.SyncToolSpecification astTool() {
        McpSchema.Tool tool = tool(
                "ast",
                "Parse Java AST",
                "Parse Java source and return a simplified AST view for structural inspection.",
                Map.of("code", stringProperty("Java code snippet")),
                List.of("code"),
                astOutputSchema(),
                readOnlyToolAnnotations()
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> safeToolCall(() -> {
            Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
            String code = requireNonBlank(stringArg(args, "code"), "code");
            return success(astService.parse(code));
        }));
    }

    private McpServerFeatures.SyncToolSpecification symbolsTool() {
        McpSchema.Tool tool = tool(
                "symbols",
                "Extract Java Symbols",
                "Extract Java classes, methods, and call-graph edges for dependency and usage questions.",
                Map.of("code", stringProperty("Java code snippet")),
                List.of("code"),
                symbolsOutputSchema(),
                readOnlyToolAnnotations()
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> safeToolCall(() -> {
            Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
            String code = requireNonBlank(stringArg(args, "code"), "code");
            return success(symbolGraphService.extract(code));
        }));
    }

    private McpServerFeatures.SyncToolSpecification migrationAssistantTool() {
        McpSchema.Tool tool = tool(
                "migration-assistant",
                "Java Migration Assistant",
                "Assess Java and Spring Boot migration readiness, especially upgrades to Java 25 and Spring Boot 4.",
                Map.of(
                        "buildFile", stringProperty("Build file content (Gradle/Maven)"),
                        "buildFilePath", stringProperty("Optional build file path, e.g. build.gradle"),
                        "code", stringProperty("Optional Java code snippet for migration checks"),
                        "targetJavaVersion", integerProperty("Target Java major version (default 25)"),
                        "targetSpringBootVersion", stringProperty("Target Spring Boot version (default 4.0.0)"),
                        "includeDocs", booleanProperty("When true, include reference docs in the response")
                ),
                List.of(),
                migrationAssistantOutputSchema(),
                readOnlyToolAnnotations()
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> safeToolCall(() -> {
            Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
            MigrationAssistantRequest payload = new MigrationAssistantRequest(
                    stringArg(args, "buildFile"),
                    stringArg(args, "buildFilePath"),
                    stringArg(args, "code"),
                    integerArg(args, "targetJavaVersion"),
                    stringArg(args, "targetSpringBootVersion"),
                    booleanArgNullable(args, "includeDocs")
            );
            return success(migrationAssistantService.assess(payload));
        }));
    }

    private McpServerFeatures.SyncToolSpecification indexStatsTool() {
        McpSchema.Tool tool = tool(
                "index-stats",
                "Index Stats",
                "Return index statistics and the last indexed timestamp.",
                Map.of(),
                List.of(),
                indexStatsOutputSchema(),
                readOnlyToolAnnotations()
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) ->
                safeToolCall(() -> success(indexLifecycleService.currentStats()))
        );
    }

    private McpServerFeatures.SyncToolSpecification rebuildIndexTool() {
        McpSchema.Tool tool = tool(
                "index-rebuild",
                "Rebuild Index",
                "Rebuild the Lucene index from ingested documents.",
                Map.of(),
                List.of(),
                indexStatsOutputSchema(),
                stateChangingToolAnnotations()
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) ->
                safeToolCall(() -> success(indexLifecycleService.rebuildIndex()))
        );
    }

    private McpServerFeatures.SyncToolSpecification manifestTool() {
        McpSchema.Tool tool = tool(
                "manifest",
                "MCP Manifest",
                "Return the MCP server catalog manifest.",
                Map.of(),
                List.of(),
                manifestOutputSchema(),
                readOnlyToolAnnotations()
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) ->
                safeToolCall(() -> success(mcpCatalogService.manifest()))
        );
    }

    private McpSchema.ReadResourceResult readResource(String resourceIdOrUri) {
        McpResourceResponse resource = mcpCatalogService.getResource(resourceIdOrUri);
        McpSchema.TextResourceContents contents = new McpSchema.TextResourceContents(
                resource.resource().uri(),
                DEFAULT_RESOURCE_MIME_TYPE,
                resource.content()
        );
        return new McpSchema.ReadResourceResult(List.of(contents));
    }

    private McpSchema.CallToolResult safeToolCall(ToolCall call) {
        try {
            return call.execute();
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "Tool execution failed" : ex.getMessage();
            return McpSchema.CallToolResult.builder()
                    .isError(true)
                    .structuredContent(Map.of("error", message))
                    .addTextContent("Error: " + message)
                    .build();
        }
    }

    private McpSchema.CallToolResult success(Object payload) {
        return success(payload, resourceLinksForPayload(payload));
    }

    private McpSchema.CallToolResult success(Object payload, List<McpSchema.Content> extraContent) {
        McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                .isError(false)
                .structuredContent(payload)
                .addTextContent(toJson(payload));
        if (extraContent != null) {
            for (McpSchema.Content content : extraContent) {
                builder.addContent(content);
            }
        }
        return builder.build();
    }

    private List<McpSchema.Content> resourceLinksForPayload(Object payload) {
        if (payload instanceof JavaDocsToolResponse response) {
            return resourceLinksForDocs(response.documents());
        }
        if (payload instanceof LibraryDocsResponse response) {
            return resourceLinksForDocs(response.documents());
        }
        if (payload instanceof SearchResponse response) {
            return resourceLinksForDocs(response.results().stream().map(this::toLibraryDoc).toList());
        }
        return List.of();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception ignored) {
            return String.valueOf(value);
        }
    }

    private String renderTemplate(String template, String variablesJson) {
        if (variablesJson == null || variablesJson.isBlank()) {
            return template;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> variables = objectMapper.readValue(variablesJson, Map.class);
            String rendered = template;
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                String replacement = Objects.toString(entry.getValue(), "");
                rendered = rendered.replace(placeholder, replacement);
            }
            return rendered;
        } catch (Exception ex) {
            return template + "\n\n[variablesJson parse error: " + ex.getMessage() + "]";
        }
    }

    private McpSchema.Tool tool(String name,
                                String title,
                                String description,
                                Map<String, Object> properties,
                                List<String> required,
                                Map<String, Object> outputSchema,
                                McpSchema.ToolAnnotations annotations) {
        McpSchema.Tool.Builder builder = McpSchema.Tool.builder()
                .name(name)
                .title(title)
                .description(description)
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        properties,
                        required,
                        false,
                        null,
                        null
                ))
                .annotations(annotations);
        if (outputSchema != null && !outputSchema.isEmpty()) {
            builder.outputSchema(outputSchema);
        }
        return builder.build();
    }

    private McpSchema.ToolAnnotations readOnlyToolAnnotations() {
        return new McpSchema.ToolAnnotations(null, true, false, true, false, false);
    }

    private McpSchema.ToolAnnotations stateChangingToolAnnotations() {
        return new McpSchema.ToolAnnotations(null, false, false, true, false, false);
    }

    private List<McpSchema.Content> resourceLinksForDocs(List<com.example.javamcp.model.LibraryDoc> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<McpSchema.Content> links = new ArrayList<>();
        for (com.example.javamcp.model.LibraryDoc document : documents.stream().limit(5).toList()) {
            String id = blankToNull(document.id());
            if (id == null) {
                continue;
            }
            String description = firstNonBlank(
                    blankToNull(document.source()),
                    blankToNull(document.sourceUrl()),
                    blankToNull(document.excerpt())
            );
            links.add(McpSchema.ResourceLink.builder()
                    .name(id)
                    .title(blankToNull(document.title()))
                    .uri("mcp://docs/" + id)
                    .description(description)
                    .mimeType(DEFAULT_RESOURCE_MIME_TYPE)
                    .annotations(new McpSchema.Annotations(List.of(McpSchema.Role.ASSISTANT), 0.95))
                    .build());
        }
        return links;
    }

    private com.example.javamcp.model.LibraryDoc toLibraryDoc(SearchResult result) {
        return new com.example.javamcp.model.LibraryDoc(
                result.id(),
                result.title(),
                result.snippet(),
                result.source(),
                result.sourceUrl(),
                result.version(),
                result.score(),
                result.score(),
                result.score(),
                List.of()
        );
    }

    private Map<String, Object> stringProperty(String description) {
        return property("string", description);
    }

    private Map<String, Object> nullableStringProperty(String description) {
        return property(List.of("string", "null"), description);
    }

    private Map<String, Object> integerProperty(String description) {
        return property("integer", description);
    }

    private Map<String, Object> numberProperty(String description) {
        return property("number", description);
    }

    private Map<String, Object> booleanProperty(String description) {
        return property("boolean", description);
    }

    private Map<String, Object> arrayOfStringsProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "array");
        property.put("description", description);
        property.put("items", Map.of("type", "string"));
        return property;
    }

    private Map<String, Object> enumProperty(String description, List<String> values) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        property.put("enum", values);
        return property;
    }

    private Map<String, Object> objectProperty(String description,
                                               Map<String, Object> properties,
                                               List<String> required) {
        return objectProperty("object", description, properties, required);
    }

    private Map<String, Object> nullableObjectProperty(String description,
                                                       Map<String, Object> properties,
                                                       List<String> required) {
        return objectProperty(List.of("object", "null"), description, properties, required);
    }

    private Map<String, Object> objectProperty(Object type,
                                               String description,
                                               Map<String, Object> properties,
                                               List<String> required) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        property.put("properties", properties);
        property.put("required", required);
        property.put("additionalProperties", false);
        return property;
    }

    private Map<String, Object> arrayProperty(String description, Map<String, Object> items) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "array");
        property.put("description", description);
        property.put("items", items);
        return property;
    }

    private Map<String, Object> outputSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> javaDocsOutputSchema() {
        return outputSchema(Map.of(
                "query", stringProperty("Resolved query"),
                "libraryHint", nullableStringProperty("Optional library hint from the caller"),
                "resolvedLibraryId", nullableStringProperty("Resolved canonical library id"),
                "resolvedLibraryName", nullableStringProperty("Resolved library name"),
                "strategy", stringProperty("Retrieval strategy used"),
                "count", integerProperty("Returned document count"),
                "context", stringProperty("Merged textual context"),
                "documents", arrayProperty("Retrieved documents", libraryDocSchema())
        ), List.of("query", "strategy", "count", "context", "documents"));
    }

    private Map<String, Object> resolveLibraryOutputSchema() {
        return outputSchema(Map.of(
                "query", stringProperty("Original query"),
                "count", integerProperty("Candidate count"),
                "libraries", arrayProperty("Resolved library candidates", libraryCandidateSchema())
        ), List.of("query", "count", "libraries"));
    }

    private Map<String, Object> libraryDocsOutputSchema() {
        return outputSchema(Map.of(
                "libraryId", stringProperty("Canonical library id"),
                "libraryName", stringProperty("Resolved library name"),
                "topic", nullableStringProperty("Requested topic"),
                "strategy", stringProperty("Retrieval strategy"),
                "alpha", numberProperty("Hybrid rerank alpha"),
                "count", integerProperty("Returned document count"),
                "approxTokens", integerProperty("Approximate token count"),
                "context", stringProperty("Merged textual context"),
                "documents", arrayProperty("Retrieved documents", libraryDocSchema())
        ), List.of("libraryId", "libraryName", "strategy", "alpha", "count", "approxTokens", "context", "documents"));
    }

    private Map<String, Object> searchOutputSchema() {
        return outputSchema(Map.of(
                "query", stringProperty("Executed search query"),
                "count", integerProperty("Result count"),
                "results", arrayProperty("Matched documents", searchResultSchema()),
                "diagnostics", nullableObjectProperty("Optional search diagnostics", Map.of(
                        "mode", stringProperty("Search mode"),
                        "expandedQuery", stringProperty("Expanded query"),
                        "lexicalCandidates", integerProperty("Lexical candidate count"),
                        "vectorCandidates", integerProperty("Vector candidate count"),
                        "elapsedMillis", integerProperty("Elapsed time in milliseconds")
                ), List.of("mode", "expandedQuery", "lexicalCandidates", "vectorCandidates", "elapsedMillis"))
        ), List.of("query", "count", "results"));
    }

    private Map<String, Object> analyzeOutputSchema() {
        return outputSchema(Map.of(
                "file", nullableStringProperty("Analyzed file name"),
                "issueCount", integerProperty("Issue count"),
                "issues", arrayProperty("Detected issues", ruleIssueSchema())
        ), List.of("issueCount", "issues"));
    }

    private Map<String, Object> astOutputSchema() {
        return outputSchema(Map.of(
                "classCount", integerProperty("Parsed class count"),
                "classes", arrayProperty("Parsed classes", astClassSchema())
        ), List.of("classCount", "classes"));
    }

    private Map<String, Object> symbolsOutputSchema() {
        return outputSchema(Map.of(
                "nodeCount", integerProperty("Node count"),
                "edgeCount", integerProperty("Edge count"),
                "nodes", arrayProperty("Symbol nodes", symbolNodeSchema()),
                "edges", arrayProperty("Symbol edges", symbolEdgeSchema())
        ), List.of("nodeCount", "edgeCount", "nodes", "edges"));
    }

    private Map<String, Object> migrationAssistantOutputSchema() {
        return outputSchema(Map.of(
                "buildTool", nullableStringProperty("Detected build tool"),
                "detectedJavaVersion", nullableStringProperty("Detected Java version"),
                "targetJavaVersion", nullableStringProperty("Target Java version"),
                "detectedSpringBootVersion", nullableStringProperty("Detected Spring Boot version"),
                "targetSpringBootVersion", nullableStringProperty("Target Spring Boot version"),
                "issueCount", integerProperty("Migration issue count"),
                "findings", arrayProperty("Migration findings", migrationFindingSchema()),
                "recommendedActions", arrayProperty("Recommended actions", Map.of("type", "string")),
                "references", arrayProperty("Supporting references", migrationReferenceSchema())
        ), List.of("issueCount", "findings", "recommendedActions", "references"));
    }

    private Map<String, Object> indexStatsOutputSchema() {
        return outputSchema(Map.of(
                "documentCount", integerProperty("Indexed document count"),
                "versions", arrayProperty("Known versions", Map.of("type", "string")),
                "tags", arrayProperty("Known tags", Map.of("type", "string")),
                "sources", arrayProperty("Known sources", Map.of("type", "string")),
                "lastIndexedAt", stringProperty("Last index timestamp")
        ), List.of("documentCount", "versions", "tags", "sources", "lastIndexedAt"));
    }

    private Map<String, Object> manifestOutputSchema() {
        return outputSchema(Map.of(
                "serverName", stringProperty("Server name"),
                "version", stringProperty("Manifest version"),
                "generatedAt", stringProperty("Manifest generation timestamp"),
                "tools", arrayProperty("Published tools", toolDescriptorSchema()),
                "toolRules", arrayProperty("Tool invocation rules", toolRuleSchema()),
                "resources", arrayProperty("Published resources", resourceDescriptorSchema()),
                "prompts", arrayProperty("Published prompts", promptTemplateSchema())
        ), List.of("serverName", "version", "generatedAt", "tools", "toolRules", "resources", "prompts"));
    }

    private Map<String, Object> libraryDocSchema() {
        return objectProperty("Library document", Map.of(
                "id", stringProperty("Document id"),
                "title", nullableStringProperty("Document title"),
                "excerpt", nullableStringProperty("Document excerpt"),
                "source", nullableStringProperty("Source name"),
                "sourceUrl", nullableStringProperty("Source URL"),
                "version", nullableStringProperty("Document version"),
                "score", numberProperty("Combined score"),
                "retrievalScore", numberProperty("Retrieval score"),
                "rerankScore", numberProperty("Rerank score"),
                "matchedTerms", arrayProperty("Matched terms", Map.of("type", "string"))
        ), List.of("id", "score", "retrievalScore", "rerankScore", "matchedTerms"));
    }

    private Map<String, Object> libraryCandidateSchema() {
        return objectProperty("Library candidate", Map.of(
                "libraryId", stringProperty("Canonical library id"),
                "name", stringProperty("Library name"),
                "summary", nullableStringProperty("Library summary"),
                "documentCount", integerProperty("Document count"),
                "versions", arrayProperty("Known versions", Map.of("type", "string")),
                "sources", arrayProperty("Known sources", Map.of("type", "string")),
                "score", numberProperty("Candidate score")
        ), List.of("libraryId", "name", "documentCount", "versions", "sources", "score"));
    }

    private Map<String, Object> searchResultSchema() {
        return objectProperty("Search result", Map.of(
                "id", stringProperty("Result id"),
                "title", nullableStringProperty("Result title"),
                "snippet", nullableStringProperty("Result snippet"),
                "source", nullableStringProperty("Source name"),
                "sourceUrl", nullableStringProperty("Source URL"),
                "version", nullableStringProperty("Document version"),
                "score", numberProperty("Result score")
        ), List.of("id", "score"));
    }

    private Map<String, Object> ruleIssueSchema() {
        return objectProperty("Rule issue", Map.of(
                "rule", stringProperty("Rule id"),
                "line", integerProperty("Line number"),
                "severity", stringProperty("Issue severity"),
                "message", stringProperty("Issue message"),
                "suggestion", stringProperty("Suggested fix")
        ), List.of("rule", "line", "severity", "message", "suggestion"));
    }

    private Map<String, Object> astClassSchema() {
        return objectProperty("AST class", Map.of(
                "name", stringProperty("Class name"),
                "packageName", nullableStringProperty("Package name"),
                "methods", arrayProperty("Declared methods", astMethodSchema())
        ), List.of("name", "packageName", "methods"));
    }

    private Map<String, Object> astMethodSchema() {
        return objectProperty("AST method", Map.of(
                "name", stringProperty("Method name"),
                "signature", stringProperty("Method signature"),
                "returnType", nullableStringProperty("Return type"),
                "javadoc", nullableStringProperty("Method javadoc")
        ), List.of("name", "signature"));
    }

    private Map<String, Object> symbolNodeSchema() {
        return objectProperty("Symbol node", Map.of(
                "id", stringProperty("Node id"),
                "type", stringProperty("Node type"),
                "name", stringProperty("Simple name"),
                "qualifiedName", stringProperty("Qualified name")
        ), List.of("id", "type", "name", "qualifiedName"));
    }

    private Map<String, Object> symbolEdgeSchema() {
        return objectProperty("Symbol edge", Map.of(
                "from", stringProperty("Source node id"),
                "to", stringProperty("Target node id"),
                "relation", stringProperty("Edge relation")
        ), List.of("from", "to", "relation"));
    }

    private Map<String, Object> migrationFindingSchema() {
        return objectProperty("Migration finding", Map.of(
                "code", stringProperty("Finding code"),
                "severity", stringProperty("Finding severity"),
                "message", stringProperty("Finding message"),
                "recommendation", stringProperty("Recommendation"),
                "detectedValue", nullableStringProperty("Detected value"),
                "targetValue", nullableStringProperty("Target value")
        ), List.of("code", "severity", "message", "recommendation"));
    }

    private Map<String, Object> migrationReferenceSchema() {
        return objectProperty("Migration reference", Map.of(
                "libraryId", nullableStringProperty("Library id"),
                "title", stringProperty("Reference title"),
                "sourceUrl", stringProperty("Reference URL"),
                "version", nullableStringProperty("Reference version"),
                "score", numberProperty("Reference score")
        ), List.of("title", "sourceUrl", "score"));
    }

    private Map<String, Object> toolDescriptorSchema() {
        return objectProperty("Tool descriptor", Map.of(
                "name", stringProperty("Tool name"),
                "description", stringProperty("Tool description"),
                "inputSchemaHint", stringProperty("Input schema hint")
        ), List.of("name", "description", "inputSchemaHint"));
    }

    private Map<String, Object> toolRuleSchema() {
        return objectProperty("Tool invocation rule", Map.of(
                "id", stringProperty("Rule id"),
                "description", stringProperty("Rule description"),
                "triggerPatterns", arrayProperty("Trigger patterns", Map.of("type", "string")),
                "toolName", stringProperty("Target tool name"),
                "priority", integerProperty("Rule priority")
        ), List.of("id", "description", "triggerPatterns", "toolName", "priority"));
    }

    private Map<String, Object> promptTemplateSchema() {
        return objectProperty("Prompt template", Map.of(
                "id", stringProperty("Prompt id"),
                "name", stringProperty("Prompt name"),
                "description", stringProperty("Prompt description"),
                "template", stringProperty("Prompt template")
        ), List.of("id", "name", "description", "template"));
    }

    private Map<String, Object> resourceDescriptorSchema() {
        return objectProperty("Resource descriptor", Map.of(
                "resourceId", stringProperty("Resource id"),
                "uri", stringProperty("Resource URI"),
                "title", nullableStringProperty("Resource title"),
                "version", nullableStringProperty("Resource version"),
                "tags", arrayProperty("Resource tags", Map.of("type", "string")),
                "source", nullableStringProperty("Source name"),
                "sourceUrl", nullableStringProperty("Source URL")
        ), List.of("resourceId", "uri", "tags"));
    }

    private Map<String, Object> property(Object type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private List<String> tagsArg(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof String stringValue) {
            return splitTags(stringValue);
        }
        if (value instanceof Collection<?> collection) {
            List<String> tags = new ArrayList<>();
            for (Object element : collection) {
                if (element == null) {
                    continue;
                }
                tags.addAll(splitTags(String.valueOf(element)));
            }
            return tags;
        }
        return splitTags(String.valueOf(value));
    }

    private List<String> splitTags(String tagsRaw) {
        if (tagsRaw == null || tagsRaw.isBlank()) {
            return List.of();
        }
        String[] split = tagsRaw.split(",");
        List<String> tags = new ArrayList<>();
        for (String tag : split) {
            if (tag != null && !tag.isBlank()) {
                tags.add(tag.trim());
            }
        }
        return tags;
    }

    private String stringArg(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private Integer integerArg(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer for '" + key + "': " + value);
        }
    }

    private Double doubleArg(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid number for '" + key + "': " + value);
        }
    }

    private boolean booleanArg(Map<String, Object> arguments, String key, boolean defaultValue) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private Boolean booleanArgNullable(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String normalized = String.valueOf(value).trim();
        if (normalized.isBlank()) {
            return null;
        }
        return Boolean.parseBoolean(normalized);
    }

    private String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("'" + field + "' is required");
        }
        return value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String resourceDescription(McpResourceDescriptor descriptor) {
        List<String> parts = new ArrayList<>();
        if (descriptor.source() != null && !descriptor.source().isBlank()) {
            parts.add(descriptor.source());
        }
        if (descriptor.version() != null && !descriptor.version().isBlank()) {
            parts.add("version " + descriptor.version());
        }
        if (descriptor.sourceUrl() != null && !descriptor.sourceUrl().isBlank()) {
            parts.add(descriptor.sourceUrl());
        }
        String joined = String.join(" | ", parts);
        return joined.isBlank() ? null : joined;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    @FunctionalInterface
    private interface ToolCall {
        McpSchema.CallToolResult execute();
    }
}
