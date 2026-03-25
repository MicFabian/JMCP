package com.example.javamcp.api;

import com.example.javamcp.model.SearchResponse;
import com.example.javamcp.search.LuceneSearchService;
import com.example.javamcp.search.SearchMode;
import com.example.javamcp.search.SearchQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api")
@Tag(name = "Search", description = "Hybrid lexical/vector search endpoints")
public class SearchController {

    private final LuceneSearchService luceneSearchService;

    public SearchController(LuceneSearchService luceneSearchService) {
        this.luceneSearchService = luceneSearchService;
    }

    @GetMapping("/search")
    @Operation(
            summary = "Search MCP knowledge",
            description = "Runs lexical/vector/hybrid retrieval with optional version, source, and tag filters.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Search completed",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = "{\"query\":\"constructor\",\"count\":1,\"results\":[{\"id\":\"spring-constructor-injection\",\"title\":\"Use Constructor Injection\",\"snippet\":\"Constructor injection keeps beans immutable...\",\"source\":\"Spring Framework Reference\",\"sourceUrl\":\"https://docs.spring.io/...\",\"version\":\"4.0.0\",\"score\":0.97}],\"diagnostics\":{\"mode\":\"HYBRID\",\"expandedQuery\":\"constructor\",\"lexicalCandidates\":1,\"vectorCandidates\":3,\"elapsedMillis\":4}}")
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid request parameters",
                            content = @Content(
                                    mediaType = "application/problem+json",
                                    examples = @ExampleObject(value = "{\"type\":\"about:blank\",\"title\":\"Bad request\",\"status\":400,\"detail\":\"Invalid request argument\"}")
                            )
                    )
            }
    )
    public SearchResponse search(@RequestParam(name = "q", required = false) String query,
                                 @Parameter(description = "Maximum number of results (capped by server max limit)")
                                 @RequestParam(name = "limit", required = false) Integer limit,
                                 @Parameter(description = "Exact version filter, e.g. 4.0.0")
                                 @RequestParam(name = "version", required = false) String version,
                                 @Parameter(description = "Tag filters, can be repeated or comma-separated")
                                 @RequestParam(name = "tags", required = false) List<String> tags,
                                 @Parameter(description = "Exact source filter")
                                 @RequestParam(name = "source", required = false) String source,
                                 @Parameter(description = "Retrieval mode: HYBRID, LEXICAL, VECTOR", schema = @Schema(implementation = String.class))
                                 @RequestParam(name = "mode", required = false, defaultValue = "HYBRID") String mode,
                                 @Parameter(description = "Include retrieval diagnostics in response")
                                 @RequestParam(name = "diagnostics", required = false, defaultValue = "false") boolean diagnostics) {
        SearchQuery searchQuery = new SearchQuery(
                query,
                limit,
                version,
                normalizeTags(tags),
                source,
                parseMode(mode),
                diagnostics
        );
        return luceneSearchService.search(searchQuery);
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

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String[] split = tag.split(",");
            for (String item : split) {
                if (!item.isBlank()) {
                    normalized.add(item.trim());
                }
            }
        }
        return normalized;
    }
}
