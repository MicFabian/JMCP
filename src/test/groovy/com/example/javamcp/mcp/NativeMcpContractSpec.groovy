package com.example.javamcp.mcp

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import spock.lang.Specification
import tools.jackson.databind.ObjectMapper

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicLong

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = ['grpc.server.port=0']
)
class NativeMcpContractSpec extends Specification {

    @LocalServerPort
    int port

    private final ObjectMapper objectMapper = new ObjectMapper()
    private final HttpClient client = HttpClient.newHttpClient()
    private final AtomicLong idSequence = new AtomicLong(100)

    def 'should initialize native MCP session with expected capabilities'() {
        when:
        def session = openInitializedSession()

        then:
        session.initializeResult().protocolVersion == '2025-06-18'
        session.initializeResult().serverInfo.name == 'java-mcp'
        session.initializeResult().capabilities.tools.listChanged == true
        session.initializeResult().capabilities.resources.listChanged == true
        session.initializeResult().capabilities.prompts.listChanged == true
    }

    def 'should execute end-to-end MCP tool flow'() {
        given:
        def session = openInitializedSession()

        when: 'list tools'
        def toolsList = rpc(session.id(), [
                jsonrpc: '2.0',
                id     : nextId(),
                method : 'tools/list',
                params : [:]
        ])
        def toolsResult = (Map) toolsList.result
        def tools = (List<Map<String, Object>>) toolsResult.tools
        def toolNames = tools.collect { it.name as String }

        then:
        toolNames.containsAll([
                'java-docs',
                'resolve-library-id',
                'query-docs',
                'search',
                'analyze',
                'ast',
                'symbols',
                'migration-assistant',
                'index-stats',
                'index-rebuild',
                'manifest'
        ])
        def javaDocsTool = tools.find { it.name == 'java-docs' } as Map<String, Object>
        javaDocsTool.outputSchema != null
        ((Map<String, Object>) javaDocsTool.inputSchema).additionalProperties == false
        ((Map<String, Object>) javaDocsTool.annotations).readOnlyHint == true
        def rebuildTool = tools.find { it.name == 'index-rebuild' } as Map<String, Object>
        ((Map<String, Object>) rebuildTool.annotations).readOnlyHint == false

        when: 'resolve a library id'
        def resolveResult = successfulToolCall(session.id(), 'resolve-library-id', [
                query: 'spring security csrf',
                limit: 3
        ])
        def libraries = (List<Map<String, Object>>) resolveResult.libraries

        then:
        resolveResult.count >= 1
        libraries*.libraryId.contains('/spring-projects/spring-security')

        when: 'resolve snappo for snapshot testing guidance'
        def snappoResolve = successfulToolCall(session.id(), 'resolve-library-id', [
                query: 'snappo snapshot testing',
                limit: 3
        ])
        def snappoLibraries = (List<Map<String, Object>>) snappoResolve.libraries

        then:
        snappoResolve.count >= 1
        snappoLibraries*.libraryId.contains('/io.github.micfabian/snappo')

        when: 'query docs with high-level java-docs tool'
        def javaDocs = successfulToolCall(session.id(), 'java-docs', [
                query      : 'how do I configure csrf in spring security',
                libraryName: 'spring security',
                limit      : 3
        ])
        def javaDocIds = ((List<Map<String, Object>>) javaDocs.documents)*.id

        then:
        javaDocs.strategy == 'resolved-library'
        javaDocs.resolvedLibraryId == '/spring-projects/spring-security'
        javaDocIds.contains('spring-boot-csrf')

        when: 'query docs for groovy snapshot testing guidance'
        def snappoDocs = successfulToolCall(session.id(), 'java-docs', [
                query      : 'how do I add snapshot testing in groovy',
                libraryName: 'snappo',
                limit      : 3
        ])
        def snappoDocIds = ((List<Map<String, Object>>) snappoDocs.documents)*.id

        then:
        snappoDocs.strategy == 'resolved-library'
        snappoDocs.resolvedLibraryId == '/io.github.micfabian/snappo'
        snappoDocIds.contains('snappo-snapshot-testing')

        when: 'query docs with raw MCP response to inspect resource links'
        def javaDocsRpc = rpc(session.id(), [
                jsonrpc: '2.0',
                id     : nextId(),
                method : 'tools/call',
                params : [
                        name     : 'java-docs',
                        arguments: [
                                query      : 'how do I configure csrf in spring security',
                                libraryName: 'spring security',
                                limit      : 3
                        ]
                ]
        ])
        def javaDocsRpcResult = (Map<String, Object>) javaDocsRpc.result
        def javaDocsContent = (List<Map<String, Object>>) javaDocsRpcResult.content

        then:
        javaDocsRpcResult.isError == false
        javaDocsContent.find { it.type == 'resource_link' && it.uri == 'mcp://docs/spring-boot-csrf' } != null

        when: 'query scoped docs'
        def scopedDocs = successfulToolCall(session.id(), 'query-docs', [
                libraryId: '/spring-projects/spring-security',
                query    : 'csrf',
                limit    : 3
        ])
        def docIds = ((List<Map<String, Object>>) scopedDocs.documents)*.id

        then:
        scopedDocs.count >= 1
        docIds.contains('spring-boot-csrf')
        scopedDocs.context.contains('Spring Security')

        when: 'run hybrid search'
        def searchResult = successfulToolCall(session.id(), 'search', [
                q          : 'csrf',
                limit      : 3,
                diagnostics: true
        ])
        def searchDocIds = ((List<Map<String, Object>>) searchResult.results)*.id

        then:
        searchResult.count >= 1
        searchDocIds.contains('spring-boot-csrf')
        searchResult.diagnostics != null

        when: 'run analyze'
        String analyzedCode = '''
            import org.springframework.beans.factory.annotation.Autowired;
            class DemoService {
                @Autowired
                private Object dependency;
                void run() {
                    System.out.println("debug");
                }
            }
            '''.stripIndent()
        def analyzeResult = successfulToolCall(session.id(), 'analyze', [code: analyzedCode])
        def issueRules = ((List<Map<String, Object>>) analyzeResult.issues)*.rule

        then:
        analyzeResult.issueCount >= 2
        issueRules.contains('avoid-field-injection')
        issueRules.contains('no-system-out')

        when: 'run ast parser'
        String astCode = '''
            package demo;
            class DemoAst {
                String run() { return "ok"; }
            }
            '''.stripIndent()
        def astResult = successfulToolCall(session.id(), 'ast', [code: astCode])
        def astClassNames = ((List<Map<String, Object>>) astResult.classes)*.name

        then:
        astResult.classCount == 1
        astClassNames == ['DemoAst']

        when: 'run symbols extraction'
        String symbolsCode = '''
            package demo;
            class Base {}
            class DemoSymbols extends Base {
                void run() { helper(); }
                void helper() {}
            }
            '''.stripIndent()
        def symbolsResult = successfulToolCall(session.id(), 'symbols', [code: symbolsCode])
        def relations = ((List<Map<String, Object>>) symbolsResult.edges)*.relation

        then:
        symbolsResult.nodeCount > 0
        relations.contains('extends')
        relations.contains('calls')

        when: 'run migration assistant'
        def migrationResult = successfulToolCall(session.id(), 'migration-assistant', [
                buildFile               : "plugins { id 'org.springframework.boot' version '3.3.2' }\njava { toolchain { languageVersion = JavaLanguageVersion.of(17) } }",
                buildFilePath           : 'build.gradle',
                code                    : 'import javax.servlet.*; class Demo {}',
                targetJavaVersion       : 25,
                targetSpringBootVersion : '4.0.0',
                includeDocs             : true
        ])
        def migrationCodes = ((List<Map<String, Object>>) migrationResult.findings)*.code

        then:
        migrationResult.issueCount >= 2
        migrationCodes.contains('java-version-upgrade-required')
        migrationCodes.contains('spring-boot-major-upgrade-required')

        when: 'inspect index stats'
        def stats = successfulToolCall(session.id(), 'index-stats', [:])

        then:
        stats.documentCount >= 3
        ((List<String>) stats.versions).contains('4.0.0')

        when: 'manifest and rebuild'
        def manifest = successfulToolCall(session.id(), 'manifest', [:])
        def rebuilt = successfulToolCall(session.id(), 'index-rebuild', [:])

        then:
        manifest.serverName == 'java-mcp'
        ((List<Map<String, Object>>) manifest.tools)*.name.contains('query-docs')
        rebuilt.documentCount >= 3
    }

    def 'should expose resources prompts and templates over native MCP transport'() {
        given:
        def session = openInitializedSession()

        when: 'list resources'
        def resourcesBody = rpc(session.id(), [
                jsonrpc: '2.0',
                id     : nextId(),
                method : 'resources/list',
                params : [:]
        ])
        def resources = (List<Map<String, Object>>) ((Map) resourcesBody.result).resources

        then:
        resources.size() >= 3
        resources*.uri.contains('mcp://docs/spring-boot-csrf')

        when: 'list templates'
        def templatesBody = rpc(session.id(), [
                jsonrpc: '2.0',
                id     : nextId(),
                method : 'resources/templates/list',
                params : [:]
        ])
        def templates = (List<Map<String, Object>>) ((Map) templatesBody.result).resourceTemplates

        then:
        templates.size() >= 1
        templates*.uriTemplate.contains('mcp://docs/{resourceId}')

        when: 'read resource by uri'
        def readBody = rpc(session.id(), [
                jsonrpc: '2.0',
                id     : nextId(),
                method : 'resources/read',
                params : [uri: 'mcp://docs/spring-boot-csrf']
        ])
        def contents = (List<Map<String, Object>>) ((Map) readBody.result).contents
        def text = (contents.first().text as String)

        then:
        contents.size() == 1
        text.contains('CSRF protection')

        when: 'list prompts'
        def promptsBody = rpc(session.id(), [
                jsonrpc: '2.0',
                id     : nextId(),
                method : 'prompts/list',
                params : [:]
        ])
        def prompts = (List<Map<String, Object>>) ((Map) promptsBody.result).prompts

        then:
        prompts*.name.containsAll(['resolve-then-query', 'migration-assistant', 'secure-config-template'])

        when: 'render a prompt with variables'
        def promptBody = rpc(session.id(), [
                jsonrpc: '2.0',
                id     : nextId(),
                method : 'prompts/get',
                params : [
                        name     : 'resolve-then-query',
                        arguments: [variablesJson: '{"query":"csrf"}']
                ]
        ])
        def messages = (List<Map<String, Object>>) ((Map) promptBody.result).messages
        def promptText = (((Map) messages.first().content).text as String)

        then:
        promptText.contains('resolve-library-id(query="csrf"')
    }

    def 'should return MCP tool error payload for invalid arguments'() {
        given:
        def session = openInitializedSession()

        when:
        def response = rpc(session.id(), [
                jsonrpc: '2.0',
                id     : nextId(),
                method : 'tools/call',
                params : [
                        name     : 'analyze',
                        arguments: [:]
                ]
        ])
        def result = (Map) response.result

        then:
        result.isError == true
        ((Map) result.structuredContent).error == "'code' is required"
    }

    private SessionContext openInitializedSession() {
        def initializeRequest = [
                jsonrpc: '2.0',
                id     : nextId(),
                method : 'initialize',
                params : [
                        protocolVersion: '2025-06-18',
                        capabilities   : [:],
                        clientInfo     : [name: 'spock', version: '1.0']
                ]
        ]

        def initializeResponse = client.send(
                jsonRequest(objectMapper.writeValueAsString(initializeRequest), null),
                HttpResponse.BodyHandlers.ofString()
        )
        assert initializeResponse.statusCode() == 200

        def initializeBody = parseMcpResponseBody(initializeResponse.body())
        def sessionId = initializeResponse.headers().firstValue('Mcp-Session-Id').orElse(
                initializeResponse.headers().firstValue('mcp-session-id').orElse(null)
        )

        def initializedNotification = [
                jsonrpc: '2.0',
                method : 'notifications/initialized',
                params : [:]
        ]
        def notificationResponse = client.send(
                jsonRequest(objectMapper.writeValueAsString(initializedNotification), sessionId),
                HttpResponse.BodyHandlers.ofString()
        )
        assert [200, 202, 204].contains(notificationResponse.statusCode())

        return new SessionContext(sessionId, (Map) initializeBody.result)
    }

    private Map<String, Object> successfulToolCall(String sessionId, String toolName, Map<String, Object> arguments) {
        def body = rpc(sessionId, [
                jsonrpc: '2.0',
                id     : nextId(),
                method : 'tools/call',
                params : [
                        name     : toolName,
                        arguments: arguments
                ]
        ])
        def result = (Map<String, Object>) body.result
        assert result != null
        assert result.isError == false
        return (Map<String, Object>) result.structuredContent
    }

    private Map rpc(String sessionId, Map payload) {
        def response = client.send(
                jsonRequest(objectMapper.writeValueAsString(payload), sessionId),
                HttpResponse.BodyHandlers.ofString()
        )
        assert response.statusCode() == 200
        return parseMcpResponseBody(response.body())
    }

    private HttpRequest jsonRequest(String payload, String sessionId) {
        def builder = HttpRequest.newBuilder(URI.create(url('/mcp')))
                .header('Content-Type', 'application/json')
                .header('Accept', 'application/json')
                .POST(HttpRequest.BodyPublishers.ofString(payload))
        if (sessionId != null && !sessionId.isBlank()) {
            builder.header('Mcp-Session-Id', sessionId)
        }
        return builder.build()
    }

    private Map parseMcpResponseBody(String body) {
        if (body == null || body.isBlank()) {
            return [:]
        }

        String trimmed = body.trim()
        if (trimmed.startsWith('{')) {
            return objectMapper.readValue(trimmed, Map)
        }

        List<String> dataLines = body.readLines()
                .findAll { it.startsWith('data:') }
                .collect { it.substring('data:'.length()).trim() }
                .findAll { !it.isBlank() }

        if (!dataLines.isEmpty()) {
            return objectMapper.readValue(dataLines.join('\n'), Map)
        }

        throw new IllegalStateException("Could not parse MCP response body: ${body}")
    }

    private String url(String path) {
        return "http://127.0.0.1:${port}${path}"
    }

    private String nextId() {
        return String.valueOf(idSequence.incrementAndGet())
    }

    private record SessionContext(String id, Map<String, Object> initializeResult) {}
}
