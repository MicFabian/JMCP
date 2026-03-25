package com.example.javamcp.mcp

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import spock.lang.Specification
import tools.jackson.databind.ObjectMapper

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = ['grpc.server.port=0']
)
class NativeMcpEndpointSpec extends Specification {

    @LocalServerPort
    int port

    def 'should initialize and list tools over native mcp endpoint'() {
        given:
        def objectMapper = new ObjectMapper()
        def client = HttpClient.newHttpClient()

        def initializeRequest = [
                jsonrpc: '2.0',
                id     : '1',
                method : 'initialize',
                params : [
                        protocolVersion: '2025-06-18',
                        capabilities   : [:],
                        clientInfo     : [name: 'spock', version: '1.0']
                ]
        ]

        when:
        def initializeResponse = client.send(
                jsonRequest(url('/mcp'), objectMapper.writeValueAsString(initializeRequest), null),
                HttpResponse.BodyHandlers.ofString()
        )
        def initializeBody = objectMapper.readValue(initializeResponse.body(), Map)

        then:
        initializeResponse.statusCode() == 200
        initializeBody.get('result') != null

        when:
        def sessionId = initializeResponse.headers().firstValue('mcp-session-id').orElse(null)
        if (sessionId == null) {
            sessionId = initializeResponse.headers().firstValue('Mcp-Session-Id').orElse(null)
        }
        assert sessionId != null && !sessionId.isBlank()

        def initializedNotification = [
                jsonrpc: '2.0',
                method : 'notifications/initialized',
                params : [:]
        ]
        client.send(
                jsonRequest(url('/mcp'), objectMapper.writeValueAsString(initializedNotification), sessionId),
                HttpResponse.BodyHandlers.ofString()
        )

        def listToolsRequest = [
                jsonrpc: '2.0',
                id     : '2',
                method : 'tools/list',
                params : [:]
        ]
        def listToolsResponse = client.send(
                jsonRequest(url('/mcp'), objectMapper.writeValueAsString(listToolsRequest), sessionId),
                HttpResponse.BodyHandlers.ofString()
        )
        def listToolsBody = parseMcpResponseBody(objectMapper, listToolsResponse.body())

        then:
        listToolsResponse.statusCode() == 200
        def result = (Map) listToolsBody.get('result')
        result != null
        def tools = (List<Map<String, Object>>) result.get('tools')
        tools*.name.containsAll(['resolve-library-id', 'query-docs', 'search', 'analyze', 'symbols'])
    }

    private String url(String path) {
        return "http://127.0.0.1:${port}${path}"
    }

    private HttpRequest jsonRequest(String url, String payload, String sessionId) {
        def builder = HttpRequest.newBuilder(URI.create(url))
                .header('Content-Type', 'application/json')
                .header('Accept', 'application/json, text/event-stream')
                .POST(HttpRequest.BodyPublishers.ofString(payload))
        if (sessionId != null && !sessionId.isBlank()) {
            builder.header('Mcp-Session-Id', sessionId)
        }
        return builder.build()
    }

    private Map parseMcpResponseBody(ObjectMapper objectMapper, String body) {
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
}
