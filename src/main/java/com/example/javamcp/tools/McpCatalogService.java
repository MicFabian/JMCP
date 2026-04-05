package com.example.javamcp.tools;

import com.example.javamcp.ingest.IngestionService;
import com.example.javamcp.model.IngestedDocument;
import com.example.javamcp.model.McpManifest;
import com.example.javamcp.model.McpResourceDescriptor;
import com.example.javamcp.model.McpResourceResponse;
import com.example.javamcp.model.PromptTemplate;
import com.example.javamcp.model.ToolDescriptor;
import com.example.javamcp.model.ToolInvocationRule;
import com.example.javamcp.search.IndexLifecycleService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class McpCatalogService {

    private static final String RESOURCE_URI_PREFIX = "mcp://docs/";
    private static final String DEFAULT_VERSION = "v0.1.0";

    private static final List<ToolDescriptor> TOOLS = List.of(
            new ToolDescriptor(
                    "java-docs",
                    "Retrieve Java, Spring, or OpenJDK docs for usage, configuration, API, best-practice, or migration questions.",
                    "{\"query\":\"how do I configure spring security csrf requestMatchers\",\"libraryName\":\"spring security\",\"limit\":5}"
            ),
            new ToolDescriptor(
                    "resolve-library-id",
                    "Resolve canonical library IDs from a query (Context7-inspired).",
                    "{\"query\":\"spring security csrf\",\"limit\":5}"
            ),
            new ToolDescriptor(
                    "query-docs",
                    "Retrieve deduplicated, reranked docs scoped to a canonical library ID.",
                    "{\"libraryId\":\"/spring-projects/spring-security\",\"query\":\"csrf\",\"tokens\":5000,\"limit\":5,\"alpha\":0.65}"
            ),
            new ToolDescriptor(
                    "search",
                    "General hybrid search across all indexed MCP content.",
                    "{\"q\":\"constructor injection\",\"mode\":\"HYBRID\",\"limit\":10}"
            ),
            new ToolDescriptor(
                    "analyze",
                    "Static rule analysis for Java source snippets.",
                    "{\"fileName\":\"MyService.java\",\"code\":\"class MyService {...}\"}"
            ),
            new ToolDescriptor(
                    "symbols",
                    "Extract Java symbol graph for structured code navigation.",
                    "{\"code\":\"class A { void run(){ helper(); } void helper(){} }\"}"
            ),
            new ToolDescriptor(
                    "migration-assistant",
                    "Assess Java/Spring migration readiness from build files and code snippets.",
                    "{\"buildFile\":\"plugins { id 'org.springframework.boot' version '3.3.2' }\",\"buildFilePath\":\"build.gradle\",\"code\":\"import javax.servlet.*; class Demo {}\",\"targetJavaVersion\":25,\"targetSpringBootVersion\":\"4.0.0\",\"includeDocs\":true}"
            )
    );

    private static final List<ToolInvocationRule> TOOL_RULES = List.of(
            new ToolInvocationRule(
                    "java-docs-direct",
                    "If the user asks how to use a Java or Spring framework feature, call java-docs directly before answering from memory.",
                    List.of("how do i", "spring security", "spring boot", "jpa", "jakarta", "virtual threads"),
                    "java-docs",
                    110
            ),
            new ToolInvocationRule(
                    "java-library-docs",
                    "If the user asks for framework/library usage or migration guidance, resolve the library first then query scoped docs.",
                    List.of("how to", "best practice", "migration", "deprecation", "spring", "java"),
                    "resolve-library-id -> query-docs",
                    100
            ),
            new ToolInvocationRule(
                    "code-smell-detection",
                    "If the user provides Java code and asks for fixes, run analyze before free-form suggestions.",
                    List.of("review this code", "fix this class", "@Autowired field", "System.out.println"),
                    "analyze",
                    90
            ),
            new ToolInvocationRule(
                    "architecture-navigation",
                    "If the user asks call-graph or dependency questions, run symbols extraction.",
                    List.of("who calls", "dependency graph", "symbol graph", "method usage"),
                    "symbols",
                    80
            ),
            new ToolInvocationRule(
                    "project-migration-audit",
                    "If the user asks for Java/Spring migration planning, run migration-assistant with the project build and code context.",
                    List.of("upgrade", "migrate", "boot 4", "java 25", "jakarta"),
                    "migration-assistant",
                    95
            )
    );

    private static final List<PromptTemplate> PROMPTS = List.of(
            new PromptTemplate(
                    "resolve-then-query",
                    "Resolve Library Then Query Docs",
                    "Two-step retrieval flow that scopes answers to official docs for one library.",
                    "1) resolve-library-id(query=\"{{query}}\", libraryName=\"{{libraryName}}\")\n"
                            + "2) query-docs(libraryId=\"{{libraryId}}\", query=\"{{query}}\", tokens={{tokens}}, limit={{limit}})\n"
                            + "3) answer with citations from returned sourceUrl values only."
            ),
            new PromptTemplate(
                    "migration-assistant",
                    "Versioned Migration Assistant",
                    "Guide migrations by combining scoped docs and static analysis.",
                    "Given project target {{targetVersion}} and code {{codeSnippet}},\n"
                            + "- run resolve-library-id + query-docs for migration notes\n"
                            + "- run analyze for rule violations\n"
                            + "- produce prioritized migration steps with code diffs."
            ),
            new PromptTemplate(
                    "secure-config-template",
                    "Secure Configuration Template Builder",
                    "Generate secure-by-default Spring config snippets from scoped docs.",
                    "Use query-docs against {{libraryId}} with query '{{query}} security defaults'.\n"
                            + "Return minimal secure config template and explain each non-default setting."
            ),
            new PromptTemplate(
                    "gradle-migration-audit",
                    "Gradle Migration Audit",
                    "Analyze a Gradle project for Java 25 and Spring Boot 4 migration readiness.",
                    "1) run migration-assistant(buildFile={{buildFile}}, buildFilePath=\"build.gradle\", code={{codeSnippet}}, targetJavaVersion=25, targetSpringBootVersion=\"4.0.0\", includeDocs=true)\n"
                            + "2) prioritize findings by severity\n"
                            + "3) convert top findings into ordered code changes with validation steps."
            )
    );

    private final IngestionService ingestionService;
    private final IndexLifecycleService indexLifecycleService;

    public McpCatalogService(IngestionService ingestionService,
                             IndexLifecycleService indexLifecycleService) {
        this.ingestionService = ingestionService;
        this.indexLifecycleService = indexLifecycleService;
    }

    public List<ToolDescriptor> listTools() {
        return TOOLS;
    }

    public List<ToolInvocationRule> listToolRules() {
        return TOOL_RULES;
    }

    public List<McpResourceDescriptor> listResources() {
        return ingestionService.loadNormalizedDocuments().stream()
                .map(this::toDescriptor)
                .sorted(Comparator.comparing(McpResourceDescriptor::resourceId))
                .toList();
    }

    public McpResourceResponse getResource(String resourceIdOrUri) {
        String resourceId = normalizeResourceId(resourceIdOrUri);
        Map<String, IngestedDocument> byId = ingestionService.loadNormalizedDocuments().stream()
                .collect(Collectors.toMap(IngestedDocument::id, Function.identity(), (left, right) -> right));

        IngestedDocument document = byId.get(resourceId);
        if (document == null) {
            throw new IllegalArgumentException("Unknown resourceId: " + resourceId);
        }

        return new McpResourceResponse(toDescriptor(document), document.content());
    }

    public List<PromptTemplate> listPrompts() {
        return PROMPTS;
    }

    public McpManifest manifest() {
        String generatedAt = indexLifecycleService.currentStats().lastIndexedAt();
        if (generatedAt == null || generatedAt.isBlank()) {
            generatedAt = Instant.now().toString();
        }

        return new McpManifest(
                "java-mcp",
                DEFAULT_VERSION,
                generatedAt,
                TOOLS,
                TOOL_RULES,
                listResources(),
                PROMPTS
        );
    }

    private McpResourceDescriptor toDescriptor(IngestedDocument document) {
        return new McpResourceDescriptor(
                document.id(),
                RESOURCE_URI_PREFIX + document.id(),
                document.title(),
                document.version(),
                document.tags(),
                document.source(),
                document.sourceUrl()
        );
    }

    private String normalizeResourceId(String resourceIdOrUri) {
        String normalized = resourceIdOrUri == null ? "" : resourceIdOrUri.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith(RESOURCE_URI_PREFIX)) {
            return normalized.substring(RESOURCE_URI_PREFIX.length());
        }
        return normalized;
    }
}
