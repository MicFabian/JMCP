package com.example.javamcp.security

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import spock.lang.Specification

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = [
                'grpc.server.port=0',
                'mcp.security.api-key=test-secret'
        ]
)
class NativeMcpSecuritySpec extends Specification {

    @LocalServerPort
    int port

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    def 'should allow authenticated native mcp POST initialize'() {
        given:
        String initialize = '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"spock","version":"1.0"}}}'

        when:
        def response = client.send(
                HttpRequest.newBuilder(URI.create(url('/mcp')))
                        .header('Content-Type', 'application/json')
                        .header('Accept', 'application/json')
                        .header('Authorization', 'Bearer test-secret')
                        .POST(HttpRequest.BodyPublishers.ofString(initialize))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        )

        then:
        response.statusCode() == 200
        response.body().contains('"jsonrpc":"2.0"')
    }

    def 'should allow authenticated native mcp GET transport handshake request through security'() {
        when:
        def response = client.send(
                HttpRequest.newBuilder(URI.create(url('/mcp')))
                        .header('Accept', 'text/event-stream')
                        .header('Authorization', 'Bearer test-secret')
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        )

        then:
        response.statusCode() != 401
        response.statusCode() != 403
    }

    private String url(String path) {
        return "http://127.0.0.1:${port}${path}"
    }
}
