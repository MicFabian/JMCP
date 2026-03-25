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
class ApiKeySecuritySpec extends Specification {

    @LocalServerPort
    int port

    private final HttpClient client = HttpClient.newHttpClient()

    def 'should require api key for application endpoints when configured'() {
        when:
        def unauthorized = client.send(
                HttpRequest.newBuilder(URI.create(url('/api/mcp/manifest')))
                        .header('Accept', 'application/json')
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        )

        then:
        unauthorized.statusCode() == 401
        unauthorized.body().contains('Missing or invalid API key')

        when:
        def authorized = client.send(
                HttpRequest.newBuilder(URI.create(url('/api/mcp/manifest')))
                        .header('Accept', 'application/json')
                        .header('X-API-Key', 'test-secret')
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        )

        then:
        authorized.statusCode() == 200
        authorized.body().contains('"serverName":"java-mcp"')

        when:
        def authorizedBearer = client.send(
                HttpRequest.newBuilder(URI.create(url('/api/mcp/manifest')))
                        .header('Accept', 'application/json')
                        .header('Authorization', 'Bearer test-secret')
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        )

        then:
        authorizedBearer.statusCode() == 200
        authorizedBearer.body().contains('"serverName":"java-mcp"')
    }

    def 'should keep health endpoint unauthenticated'() {
        when:
        def health = client.send(
                HttpRequest.newBuilder(URI.create(url('/actuator/health')))
                        .header('Accept', 'application/json')
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        )

        then:
        health.statusCode() == 200
        health.body().contains('"status":"UP"')
    }

    private String url(String path) {
        return "http://127.0.0.1:${port}${path}"
    }
}
