package com.example.javamcp.api;

import com.example.javamcp.model.LibraryDocsResponse;
import com.example.javamcp.model.ResolveLibraryResponse;
import com.example.javamcp.model.ToolInvocationRule;
import com.example.javamcp.search.SearchMode;
import com.example.javamcp.tools.LibraryToolsService;
import com.example.javamcp.tools.McpCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/tools")
@Tag(name = "Tools", description = "Context7-inspired agent tool contracts")
public class ToolsController {

    private final LibraryToolsService libraryToolsService;
    private final McpCatalogService mcpCatalogService;

    public ToolsController(LibraryToolsService libraryToolsService,
                           McpCatalogService mcpCatalogService) {
        this.libraryToolsService = libraryToolsService;
        this.mcpCatalogService = mcpCatalogService;
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
                parseMode(mode),
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
                parseMode(mode),
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
}
