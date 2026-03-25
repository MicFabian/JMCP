package com.example.javamcp.mcp;

import com.example.javamcp.analysis.AstService;
import com.example.javamcp.analysis.RuleEngineService;
import com.example.javamcp.analysis.SymbolGraphService;
import com.example.javamcp.model.LibraryDocsResponse;
import com.example.javamcp.model.McpResourceDescriptor;
import com.example.javamcp.model.McpResourceResponse;
import com.example.javamcp.model.PromptTemplate;
import com.example.javamcp.model.ResolveLibraryResponse;
import com.example.javamcp.model.SearchResponse;
import com.example.javamcp.search.IndexLifecycleService;
import com.example.javamcp.search.LuceneSearchService;
import com.example.javamcp.search.SearchMode;
import com.example.javamcp.search.SearchQuery;
import com.example.javamcp.tools.LibraryToolsService;
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

@Component
public class NativeMcpSpecificationFactory {

    private static final String DEFAULT_RESOURCE_MIME_TYPE = "text/markdown";

    private final ObjectMapper objectMapper;
    private final LuceneSearchService luceneSearchService;
    private final LibraryToolsService libraryToolsService;
    private final RuleEngineService ruleEngineService;
    private final AstService astService;
    private final SymbolGraphService symbolGraphService;
    private final IndexLifecycleService indexLifecycleService;
    private final McpCatalogService mcpCatalogService;

    public NativeMcpSpecificationFactory(ObjectMapper objectMapper,
                                         LuceneSearchService luceneSearchService,
                                         LibraryToolsService libraryToolsService,
                                         RuleEngineService ruleEngineService,
                                         AstService astService,
                                         SymbolGraphService symbolGraphService,
                                         IndexLifecycleService indexLifecycleService,
                                         McpCatalogService mcpCatalogService) {
        this.objectMapper = objectMapper;
        this.luceneSearchService = luceneSearchService;
        this.libraryToolsService = libraryToolsService;
        this.ruleEngineService = ruleEngineService;
        this.astService = astService;
        this.symbolGraphService = symbolGraphService;
        this.indexLifecycleService = indexLifecycleService;
        this.mcpCatalogService = mcpCatalogService;
    }

    public List<McpServerFeatures.SyncToolSpecification> toolSpecifications() {
        return List.of(
                resolveLibraryIdTool(),
                queryDocsTool(),
                searchTool(),
                analyzeTool(),
                astTool(),
                symbolsTool(),
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

    private McpServerFeatures.SyncToolSpecification resolveLibraryIdTool() {
        McpSchema.Tool tool = tool(
                "resolve-library-id",
                "Resolve canonical library IDs from a natural language query.",
                Map.of(
                        "query", stringProperty("Natural language query (preferred input)"),
                        "libraryName", stringProperty("Library name hint, e.g. spring security"),
                        "topic", stringProperty("Optional topic, e.g. csrf"),
                        "limit", integerProperty("Maximum number of candidates")
                ),
                List.of()
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
                "Retrieve deduplicated docs scoped to a canonical library id.",
                Map.of(
                        "libraryId", stringProperty("Canonical library id, e.g. /spring-projects/spring-security"),
                        "query", stringProperty("Query within that library"),
                        "tokens", integerProperty("Approximate token budget"),
                        "limit", integerProperty("Max number of documents"),
                        "version", stringProperty("Optional exact version filter"),
                        "mode", enumProperty("Retrieval mode", List.of("HYBRID", "LEXICAL", "VECTOR")),
                        "alpha", numberProperty("Rerank weight (0..1)")
                ),
                List.of("libraryId")
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
                    parseMode(stringArg(args, "mode")),
                    doubleArg(args, "alpha")
            );
            return success(response);
        }));
    }

    private McpServerFeatures.SyncToolSpecification searchTool() {
        McpSchema.Tool tool = tool(
                "search",
                "Hybrid lexical/vector search across indexed MCP content.",
                Map.of(
                        "q", stringProperty("Search query"),
                        "limit", integerProperty("Maximum number of results"),
                        "version", stringProperty("Optional exact version filter"),
                        "tags", arrayOfStringsProperty("Tag filters; array or comma-separated string"),
                        "source", stringProperty("Optional exact source filter"),
                        "mode", enumProperty("Retrieval mode", List.of("HYBRID", "LEXICAL", "VECTOR")),
                        "diagnostics", booleanProperty("Include diagnostics in response")
                ),
                List.of()
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
                    parseMode(stringArg(args, "mode")),
                    booleanArg(args, "diagnostics", false)
            ));
            return success(response);
        }));
    }

    private McpServerFeatures.SyncToolSpecification analyzeTool() {
        McpSchema.Tool tool = tool(
                "analyze",
                "Run static rule analysis on Java code.",
                Map.of(
                        "fileName", stringProperty("Optional file name"),
                        "code", stringProperty("Java code snippet")
                ),
                List.of("code")
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
                "Parse Java source and return a simplified AST view.",
                Map.of("code", stringProperty("Java code snippet")),
                List.of("code")
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
                "Extract symbol graph nodes and edges from Java code.",
                Map.of("code", stringProperty("Java code snippet")),
                List.of("code")
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> safeToolCall(() -> {
            Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
            String code = requireNonBlank(stringArg(args, "code"), "code");
            return success(symbolGraphService.extract(code));
        }));
    }

    private McpServerFeatures.SyncToolSpecification indexStatsTool() {
        McpSchema.Tool tool = tool(
                "index-stats",
                "Return index statistics and last indexed timestamp.",
                Map.of(),
                List.of()
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) ->
                safeToolCall(() -> success(indexLifecycleService.currentStats()))
        );
    }

    private McpServerFeatures.SyncToolSpecification rebuildIndexTool() {
        McpSchema.Tool tool = tool(
                "index-rebuild",
                "Rebuild the Lucene index from ingested documents.",
                Map.of(),
                List.of()
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) ->
                safeToolCall(() -> success(indexLifecycleService.rebuildIndex()))
        );
    }

    private McpServerFeatures.SyncToolSpecification manifestTool() {
        McpSchema.Tool tool = tool(
                "manifest",
                "Return MCP server catalog manifest.",
                Map.of(),
                List.of()
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
        return McpSchema.CallToolResult.builder()
                .isError(false)
                .structuredContent(payload)
                .addTextContent(toJson(payload))
                .build();
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
                                String description,
                                Map<String, Object> properties,
                                List<String> required) {
        return McpSchema.Tool.builder()
                .name(name)
                .title(name)
                .description(description)
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        properties,
                        required,
                        true,
                        null,
                        null
                ))
                .build();
    }

    private Map<String, Object> stringProperty(String description) {
        return property("string", description);
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

    private Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private SearchMode parseMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return SearchMode.HYBRID;
        }
        try {
            return SearchMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SearchMode.HYBRID;
        }
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
