package com.example.javamcp.api;

import com.example.javamcp.model.IndexStatsResponse;
import com.example.javamcp.search.IndexLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/index")
@Tag(name = "Index", description = "Index lifecycle and diagnostics")
public class IndexController {

    private final IndexLifecycleService indexLifecycleService;

    public IndexController(IndexLifecycleService indexLifecycleService) {
        this.indexLifecycleService = indexLifecycleService;
    }

    @GetMapping("/stats")
    @Operation(
            summary = "Get index statistics",
            responses = @ApiResponse(
                    responseCode = "200",
                    description = "Stats retrieved",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"documentCount\":3,\"versions\":[\"4.0.0\",\"25\"],\"tags\":[\"security\",\"spring-boot\"],\"sources\":[\"Spring Security Reference\"],\"lastIndexedAt\":\"2026-02-26T12:10:56.189Z\"}")
                    )
            )
    )
    public IndexStatsResponse stats() {
        return indexLifecycleService.currentStats();
    }

    @PostMapping("/rebuild")
    @Operation(
            summary = "Rebuild Lucene index from ingested docs",
            responses = @ApiResponse(
                    responseCode = "200",
                    description = "Rebuild completed",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "{\"documentCount\":3,\"versions\":[\"4.0.0\",\"25\"],\"tags\":[\"best-practice\",\"security\"],\"sources\":[\"OpenJDK JEP\",\"Spring Framework Reference\"],\"lastIndexedAt\":\"2026-02-26T12:30:00.000Z\"}")
                    )
            )
    )
    public IndexStatsResponse rebuild() {
        return indexLifecycleService.rebuildIndex();
    }
}
