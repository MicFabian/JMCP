package com.example.javamcp.api

import com.example.javamcp.model.McpManifest
import com.example.javamcp.model.McpResourceDescriptor
import com.example.javamcp.model.McpResourceResponse
import com.example.javamcp.model.PromptTemplate
import com.example.javamcp.model.ToolDescriptor
import com.example.javamcp.model.ToolInvocationRule
import com.example.javamcp.tools.McpCatalogService
import spock.lang.Specification

class McpCatalogControllerSpec extends Specification {

    def 'should expose catalog endpoints including manifest'() {
        given:
        def service = Stub(McpCatalogService) {
            listTools() >> [new ToolDescriptor('query-docs', 'desc', '{}')]
            manifest() >> new McpManifest(
                    'java-mcp',
                    'v0.1.0',
                    '2026-02-26T00:00:00Z',
                    [new ToolDescriptor('query-docs', 'desc', '{}')],
                    [new ToolInvocationRule('java-library-docs', 'desc', ['spring'], 'resolve-library-id -> query-docs', 100)],
                    [new McpResourceDescriptor('spring-boot-csrf', 'mcp://docs/spring-boot-csrf', 'Enable CSRF Protection', '4.0.0', ['security'], 'Spring Security Reference', 'https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html')],
                    [new PromptTemplate('resolve-then-query', 'Resolve Then Query', 'desc', 'template')]
            )
            listToolRules() >> [new ToolInvocationRule('java-library-docs', 'desc', ['spring'], 'resolve-library-id -> query-docs', 100)]
            listResources() >> [new McpResourceDescriptor('spring-boot-csrf', 'mcp://docs/spring-boot-csrf', 'Enable CSRF Protection', '4.0.0', ['security'], 'Spring Security Reference', 'https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html')]
            getResource(_) >> new McpResourceResponse(
                    new McpResourceDescriptor('spring-boot-csrf', 'mcp://docs/spring-boot-csrf', 'Enable CSRF Protection', '4.0.0', ['security'], 'Spring Security Reference', 'https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html'),
                    'By default, Spring Security enables CSRF protection.'
            )
            listPrompts() >> [new PromptTemplate('resolve-then-query', 'Resolve Then Query', 'desc', 'template')]
        }

        def controller = new McpCatalogController(service)

        when:
        def tools = controller.tools()
        def manifest = controller.manifest()
        def rules = controller.toolRules()
        def resources = controller.resources()
        def resource = controller.resourceByRef('mcp://docs/spring-boot-csrf')
        def prompts = controller.prompts()

        then:
        tools*.name() == ['query-docs']
        manifest.serverName() == 'java-mcp'
        rules*.id() == ['java-library-docs']
        resources*.resourceId() == ['spring-boot-csrf']
        resource.resource().uri() == 'mcp://docs/spring-boot-csrf'
        prompts*.id() == ['resolve-then-query']
    }
}
