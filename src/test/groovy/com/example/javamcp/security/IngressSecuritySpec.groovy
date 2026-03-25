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
                'mcp.security.api-key=test-secret',
                'mcp.ingress.enforce-https=true',
                'mcp.ingress.hsts-enabled=true',
                'mcp.ingress.trusted-proxies=127\\.0\\.0\\.1|::1'
        ]
)
class IngressSecuritySpec extends Specification {

    @LocalServerPort
    int port

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    def 'should redirect plain http to https when enforce-https is enabled'() {
        when:
        def response = client.send(
                HttpRequest.newBuilder(URI.create(url('/api/mcp/manifest')))
                        .header('Accept', 'application/json')
                        .header('X-API-Key', 'test-secret')
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        )

        then:
        [301, 302, 307, 308].contains(response.statusCode())
        response.headers().firstValue('location').orElse('').startsWith('https://')
    }

    def 'should accept forwarded https and emit strict transport security header'() {
        when:
        def response = client.send(
                HttpRequest.newBuilder(URI.create(url('/api/mcp/manifest')))
                        .header('Accept', 'application/json')
                        .header('X-API-Key', 'test-secret')
                        .header('X-Forwarded-Proto', 'https')
                        .header('X-Forwarded-Host', 'mcp.example.internal')
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        )

        then:
        response.statusCode() == 200
        response.headers().firstValue('strict-transport-security').orElse('').contains('max-age=')
    }

    private String url(String path) {
        return "http://127.0.0.1:${port}${path}"
    }
}
