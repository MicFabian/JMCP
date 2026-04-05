package com.example.javamcp.api;

import com.example.javamcp.model.LibraryDocsResponse;
import com.example.javamcp.model.MigrationAssistantRequest;
import com.example.javamcp.model.MigrationAssistantResponse;
import com.example.javamcp.model.ResolveLibraryResponse;
import com.example.javamcp.model.ToolInvocationRule;
import com.example.javamcp.search.SearchModeResolver;
import com.example.javamcp.tools.LibraryToolsService;
import com.example.javamcp.tools.MigrationAssistantService;
import com.example.javamcp.tools.McpCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
@Tag(name = "Tools", description = "Context7-inspired agent tool contracts")
public class ToolsController {

    private final LibraryToolsService libraryToolsService;
    private final MigrationAssistantService migrationAssistantService;
    private final McpCatalogService mcpCatalogService;
    private final SearchModeResolver searchModeResolver;

    public ToolsController(LibraryToolsService libraryToolsService,
                           MigrationAssistantService migrationAssistantService,
                           McpCatalogService mcpCatalogService,
                           SearchModeResolver searchModeResolver) {
        this.libraryToolsService = libraryToolsService;
        this.migrationAssistantService = migrationAssistantService;
        this.mcpCatalogService = mcpCatalogService;
        this.searchModeResolver = searchModeResolver;
    }

    @GetMapping("/resolve-library-id")
    @Operation(
            summary = "Resolve a canonical library ID",
            description = "Returns ranked canonical IDs (e.g. /spring-projects/spring-security) from ingested sources.",
            responses = @ApiResponse(
                    responseCode = "200",
                    description = "Library IDs resolved",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"query\":\"spring security csrf\",\"count\":1,\"libraries\":[{\"libraryId\":\"/spring-projects/spring-security\",\"name\":\"Spring Security\",\"summary\":\"Enable CSRF Protection\",\"documentCount\":1,\"versions\":[\"4.0.0\"],\"sources\":[\"Spring Security Reference\"],\"score\":0.96}]}")
                    )
            )
    )
    public ResolveLibraryResponse resolveLibraryId(@RequestParam(name = "query", required = false) String query,
                                                   @RequestParam(name = "libraryName", required = false) String libraryName,
                                                   @RequestParam(name = "topic", required = false) String topic,
                                                   @Parameter(description = "Maximum matches to return")
                                                   @RequestParam(name = "limit", required = false) Integer limit) {
        return libraryToolsService.resolveLibraryId(query, libraryName, topic, limit);
    }

    @GetMapping("/query-docs")
    @Operation(
            summary = "Query targeted docs for a resolved library ID",
            description = "Returns deduplicated and reranked context chunks constrained by library ID and token budget.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Library docs returned",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = "{\"libraryId\":\"/spring-projects/spring-security\",\"libraryName\":\"Spring Security\",\"topic\":\"csrf\",\"strategy\":\"hybrid-rerank-dedup\",\"alpha\":0.65,\"count\":1,\"approxTokens\":83,\"context\":\"# Spring Security (/spring-projects/spring-security)\\n\\nQuery: csrf\\n\\n## Enable CSRF Protection\\nSource: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html\\nVersion: 4.0.0\\n\\nBy default, Spring Security enables CSRF protection...\",\"documents\":[{\"id\":\"spring-boot-csrf\",\"title\":\"Enable CSRF Protection\",\"excerpt\":\"By default, Spring Security enables CSRF protection...\",\"source\":\"Spring Security Reference\",\"sourceUrl\":\"https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html\",\"version\":\"4.0.0\",\"score\":0.94,\"retrievalScore\":0.90,\"rerankScore\":0.94,\"matchedTerms\":[\"csrf\"]}]}")
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Unknown libraryId or invalid arguments"
                    )
            }
    )
    public LibraryDocsResponse queryDocs(@RequestParam("libraryId") String libraryId,
                                         @RequestParam(name = "query", required = false) String query,
                                         @RequestParam(name = "tokens", required = false) Integer tokens,
                                         @RequestParam(name = "limit", required = false) Integer limit,
                                         @RequestParam(name = "version", required = false) String version,
                                         @RequestParam(name = "mode", required = false, defaultValue = "HYBRID") String mode,
                                         @RequestParam(name = "alpha", required = false) Double alpha) {
        return libraryToolsService.queryDocs(
                libraryId,
                query,
                tokens,
                limit,
                version,
                searchModeResolver.resolve(mode),
                alpha
        );
    }

    @GetMapping("/get-library-docs")
    @Operation(
            summary = "Legacy alias for query-docs",
            description = "Compatibility endpoint. Prefer /api/tools/query-docs with the query parameter."
    )
    public LibraryDocsResponse getLibraryDocs(@RequestParam("libraryId") String libraryId,
                                              @RequestParam(name = "topic", required = false) String topic,
                                              @RequestParam(name = "tokens", required = false) Integer tokens,
                                              @RequestParam(name = "limit", required = false) Integer limit,
                                              @RequestParam(name = "version", required = false) String version,
                                              @RequestParam(name = "mode", required = false, defaultValue = "HYBRID") String mode) {
        return libraryToolsService.queryDocs(
                libraryId,
                topic,
                tokens,
                limit,
                version,
                searchModeResolver.resolve(mode),
                null
        );
    }

    @GetMapping("/auto-rules")
    @Operation(
            summary = "List recommended automatic tool invocation rules",
            description = "Rules clients can use to auto-trigger MCP tools for relevant prompts."
    )
    public List<ToolInvocationRule> autoRules() {
        return mcpCatalogService.listToolRules();
    }

    @PostMapping("/migration-assistant")
    @Operation(
            summary = "Assess Spring/Java migration readiness from build + code inputs",
            description = "Detects Java/Spring Boot versions, surfaces migration findings, and returns prioritized actions with doc references.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Migration assessment generated",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = "{\"buildTool\":\"GRADLE_GROOVY\",\"detectedJavaVersion\":\"17\",\"targetJavaVersion\":\"25\",\"detectedSpringBootVersion\":\"3.3.2\",\"targetSpringBootVersion\":\"4.0.0\",\"issueCount\":3,\"findings\":[{\"code\":\"java-version-upgrade-required\",\"severity\":\"HIGH\",\"message\":\"Project targets Java 17, but target is Java 25.\",\"recommendation\":\"Upgrade toolchain and compiler release to Java 25.\",\"detectedValue\":\"17\",\"targetValue\":\"25\"}],\"recommendedActions\":[\"Upgrade toolchain and compiler release to Java 25.\"],\"references\":[{\"libraryId\":\"/openjdk/jdk\",\"title\":\"Virtual Threads for Concurrent MCP Requests\",\"sourceUrl\":\"https://openjdk.org/jeps/444\",\"version\":\"25\",\"score\":0.92}]}")
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid input (both buildFile and code missing)"
                    )
            }
    )
    public MigrationAssistantResponse migrationAssistant(@Valid @org.springframework.web.bind.annotation.RequestBody
                                                         @RequestBody(
                                                                 required = true,
                                                                 content = @Content(examples = @ExampleObject(value = "{\"buildFile\":\"plugins { id 'org.springframework.boot' version '3.3.2' }\\njava { toolchain { languageVersion = JavaLanguageVersion.of(17) } }\",\"buildFilePath\":\"build.gradle\",\"code\":\"import javax.servlet.*; class Demo {}\",\"targetJavaVersion\":25,\"targetSpringBootVersion\":\"4.0.0\",\"includeDocs\":true}"))
                                                         ) MigrationAssistantRequest request) {
        return migrationAssistantService.assess(request);
    }
}
