package com.example.javamcp.api;

import com.example.javamcp.model.McpResourceDescriptor;
import com.example.javamcp.model.McpManifest;
import com.example.javamcp.model.McpResourceResponse;
import com.example.javamcp.model.PromptTemplate;
import com.example.javamcp.model.ToolDescriptor;
import com.example.javamcp.model.ToolInvocationRule;
import com.example.javamcp.tools.McpCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mcp")
@Tag(name = "MCP Catalog", description = "MCP-style discovery for tools, resources, and prompts")
public class McpCatalogController {

    private final McpCatalogService mcpCatalogService;

    public McpCatalogController(McpCatalogService mcpCatalogService) {
        this.mcpCatalogService = mcpCatalogService;
    }

    @GetMapping("/tools")
    @Operation(summary = "List available MCP tools")
    public List<ToolDescriptor> tools() {
        return mcpCatalogService.listTools();
    }

    @GetMapping("/manifest")
    @Operation(summary = "Get combined MCP manifest (tools, rules, resources, prompts)")
    public McpManifest manifest() {
        return mcpCatalogService.manifest();
    }

    @GetMapping("/tool-rules")
    @Operation(summary = "List recommended auto-invocation rules")
    public List<ToolInvocationRule> toolRules() {
        return mcpCatalogService.listToolRules();
    }

    @GetMapping("/resources")
    @Operation(summary = "List available MCP resources")
    public List<McpResourceDescriptor> resources() {
        return mcpCatalogService.listResources();
    }

    @GetMapping("/resources/{resourceId}")
    @Operation(summary = "Get one MCP resource with content")
    public McpResourceResponse resource(@PathVariable String resourceId) {
        return mcpCatalogService.getResource(resourceId);
    }

    @GetMapping("/resource")
    @Operation(summary = "Get one MCP resource by id or mcp:// URI")
    public McpResourceResponse resourceByRef(@RequestParam("ref") String resourceIdOrUri) {
        return mcpCatalogService.getResource(resourceIdOrUri);
    }

    @GetMapping("/prompts")
    @Operation(summary = "List prompt templates")
    public List<PromptTemplate> prompts() {
        return mcpCatalogService.listPrompts();
    }
}
