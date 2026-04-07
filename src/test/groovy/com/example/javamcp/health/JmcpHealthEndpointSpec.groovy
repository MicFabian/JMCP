package com.example.javamcp.health

import com.example.javamcp.model.IndexStatsResponse
import com.example.javamcp.model.IngestionSourceStatus
import com.example.javamcp.search.IndexLifecycleService
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import spock.lang.Specification

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import static org.mockito.Mockito.doReturn

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = [
                'grpc.server.port=0',
                'management.endpoint.health.show-details=always',
                'mcp.ingest.rebuild-on-startup=false',
                'mcp.ingest.schedule.enabled=true',
                'mcp.ingest.remote-sources[0].id=spring-docs',
                'mcp.ingest.remote-sources[0].url=https://docs.spring.io',
                'mcp.ingest.remote-sources[0].enabled=true',
                'mcp.ingest.remote-sources[0].fail-on-error=true'
        ]
)
class JmcpHealthEndpointSpec extends Specification {

    @LocalServerPort
    int port

    @MockitoBean
    IndexLifecycleService indexLifecycleService

    private final HttpClient client = HttpClient.newHttpClient()

    def 'should expose readiness as up when jmcp health is healthy'() {
        given:
        doReturn(new IndexStatsResponse(3, ['4.0.0'], ['spring'], ['Spring'], '2026-04-07T09:30:00Z'))
                .when(indexLifecycleService).currentStats()
        doReturn([
                new IngestionSourceStatus('spring-docs', 'remote', 'https://docs.spring.io', 'HTML', true, 2, '2026-04-07T09:30:00Z', null)
        ]).when(indexLifecycleService).sourceStatuses()

        when:
        def response = client.send(request('/actuator/health/readiness'), HttpResponse.BodyHandlers.ofString())

        then:
        response.statusCode() == HttpStatus.OK.value()
        response.body().contains('"status":"UP"')
        response.body().contains('"jmcp"')
    }

    def 'should expose readiness as down when required remote sources fail'() {
        given:
        doReturn(new IndexStatsResponse(3, ['4.0.0'], ['spring'], ['Spring'], '2026-04-07T09:30:00Z'))
                .when(indexLifecycleService).currentStats()
        doReturn([
                new IngestionSourceStatus('spring-docs', 'remote', 'https://docs.spring.io', 'HTML', true, 0, '2026-04-07T09:30:00Z', 'HTTP 503')
        ]).when(indexLifecycleService).sourceStatuses()

        when:
        def response = client.send(request('/actuator/health/readiness'), HttpResponse.BodyHandlers.ofString())

        then:
        response.statusCode() == HttpStatus.SERVICE_UNAVAILABLE.value()
        response.body().contains('"status":"DOWN"')
        response.body().contains('"remoteSources"')
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:${port}${path}"))
                .header('Accept', 'application/json')
                .GET()
                .build()
    }
}
