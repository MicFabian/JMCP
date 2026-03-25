package com.example.javamcp.api;

import com.example.javamcp.analysis.AstService;
import com.example.javamcp.analysis.RuleDefinition;
import com.example.javamcp.analysis.RuleEngineService;
import com.example.javamcp.analysis.SymbolGraphService;
import com.example.javamcp.model.AnalyzeRequest;
import com.example.javamcp.model.AnalyzeResponse;
import com.example.javamcp.model.AstRequest;
import com.example.javamcp.model.AstResponse;
import com.example.javamcp.model.SymbolGraphResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Analysis", description = "AST, rule, and symbol graph analysis endpoints")
public class AnalysisController {

    private final AstService astService;
    private final RuleEngineService ruleEngineService;
    private final SymbolGraphService symbolGraphService;

    public AnalysisController(AstService astService,
                              RuleEngineService ruleEngineService,
                              SymbolGraphService symbolGraphService) {
        this.astService = astService;
        this.ruleEngineService = ruleEngineService;
        this.symbolGraphService = symbolGraphService;
    }

    @PostMapping("/ast")
    @Operation(
            summary = "Parse Java AST",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "AST parsed",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = "{\"classCount\":1,\"classes\":[{\"name\":\"A\",\"packageName\":\"\",\"methods\":[{\"name\":\"run\",\"signature\":\"void run()\",\"returnType\":\"void\",\"javadoc\":\"\"}]}]}")
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid Java payload",
                            content = @Content(
                                    mediaType = "application/problem+json",
                                    examples = @ExampleObject(value = "{\"type\":\"about:blank\",\"title\":\"Java parse error\",\"status\":400,\"detail\":\"Could not parse Java input\"}")
                            )
                    )
            }
    )
    public AstResponse ast(@Valid @org.springframework.web.bind.annotation.RequestBody
                           @RequestBody(
                                   required = true,
                                   content = @Content(examples = @ExampleObject(value = "{\"code\":\"class A { void run(){} }\""))
                           ) AstRequest request) {
        return astService.parse(request.code());
    }

    @PostMapping("/analyze")
    @Operation(
            summary = "Analyze Java code with MCP rules",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Analysis complete",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = "{\"file\":\"MyService.java\",\"issueCount\":1,\"issues\":[{\"rule\":\"no-system-out\",\"line\":3,\"severity\":\"LOW\",\"message\":\"Avoid System.out.println in application code\",\"suggestion\":\"Use a logger such as slf4j\"}]}")
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid Java payload",
                            content = @Content(
                                    mediaType = "application/problem+json",
                                    examples = @ExampleObject(value = "{\"type\":\"about:blank\",\"title\":\"Java parse error\",\"status\":400,\"detail\":\"Could not parse Java input\"}")
                            )
                    )
            }
    )
    public AnalyzeResponse analyze(@Valid @org.springframework.web.bind.annotation.RequestBody
                                   @RequestBody(
                                           required = true,
                                           content = @Content(examples = @ExampleObject(value = "{\"fileName\":\"MyService.java\",\"code\":\"class MyService { void run(){ System.out.println(\\\"x\\\"); }}\""))
                                   ) AnalyzeRequest request) {
        return ruleEngineService.analyze(request.fileName(), request.code());
    }

    @PostMapping("/symbols")
    @Operation(
            summary = "Extract symbol graph from Java code",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Symbol graph generated",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = "{\"nodeCount\":3,\"edgeCount\":2,\"nodes\":[{\"id\":\"class:A\",\"type\":\"Class\",\"name\":\"A\",\"qualifiedName\":\"A\"}],\"edges\":[{\"from\":\"class:A\",\"to\":\"method:A#run()\",\"relation\":\"declaresMethod\"}]}")
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid Java payload",
                            content = @Content(
                                    mediaType = "application/problem+json",
                                    examples = @ExampleObject(value = "{\"type\":\"about:blank\",\"title\":\"Java parse error\",\"status\":400,\"detail\":\"Could not parse Java input\"}")
                            )
                    )
            }
    )
    public SymbolGraphResponse symbols(@Valid @org.springframework.web.bind.annotation.RequestBody
                                       @RequestBody(
                                               required = true,
                                               content = @Content(examples = @ExampleObject(value = "{\"code\":\"class A { void run(){ helper(); } void helper(){} }\""))
                                       ) AstRequest request) {
        return symbolGraphService.extract(request.code());
    }

    @GetMapping("/rules")
    @Operation(
            summary = "List active MCP analysis rules",
            responses = @ApiResponse(
                    responseCode = "200",
                    description = "Rules listed",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "[{\"id\":\"no-system-out\",\"description\":\"Avoid System.out.println in application code\",\"matchType\":\"AST_METHOD_CALL\",\"pattern\":\"System.out.print\",\"fix\":\"Use a logger such as slf4j\",\"severity\":\"LOW\",\"target\":\"MethodCall\",\"enabled\":true}]")
                    )
            )
    )
    public List<RuleDefinition> rules() {
        return ruleEngineService.listRules();
    }
}
