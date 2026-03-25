package com.example.javamcp.ingest

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import spock.lang.Specification
import tools.jackson.databind.ObjectMapper

import java.net.InetSocketAddress
import java.io.IOException

class RemoteDocumentLoaderSpec extends Specification {

    private HttpServer server
    private String baseUrl

    def setup() {
        server = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        server.createContext('/docs.json', this::handleJson)
        server.createContext('/guide.md', this::handleMarkdown)
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    def cleanup() {
        server?.stop(0)
    }

    def 'should load json array docs from remote source'() {
        given:
        def source = new IngestionProperties.RemoteSource()
        source.id = 'json-docs'
        source.url = "${baseUrl}/docs.json"
        source.format = 'json'
        source.sourceName = 'Remote JSON Docs'
        source.sourceTag = 'remote-json'
        source.version = '4.0.0'

        def loader = new RemoteDocumentLoader(new ObjectMapper())

        when:
        def documents = loader.loadRemoteDocuments([source])

        then:
        documents.size() == 2
        documents*.id().containsAll(['remote-1', 'remote-2'])
        documents.every { it.source() == 'Remote JSON Docs' }
        documents.every { it.tags().contains('remote-json') }
    }

    def 'should load markdown as text content from remote source'() {
        given:
        def source = new IngestionProperties.RemoteSource()
        source.id = 'markdown-guide'
        source.url = "${baseUrl}/guide.md"
        source.format = 'markdown'
        source.sourceName = 'Remote Markdown Guide'
        source.sourceTag = 'remote-md'
        source.version = '25'

        def loader = new RemoteDocumentLoader(new ObjectMapper())

        when:
        def documents = loader.loadRemoteDocuments([source])

        then:
        documents.size() == 1
        documents.first().title().contains('Java MCP Guide')
        documents.first().content().toLowerCase().contains('virtual threads')
        documents.first().sourceUrl() == source.url
    }

    private void handleJson(HttpExchange exchange) throws IOException {
        byte[] body = '''
[
  {
    "id": "remote-1",
    "title": "Remote Spring Security",
    "version": "4.0.0",
    "tags": ["spring", "security"],
    "content": "CSRF is enabled by default in Spring Security.",
    "sourceUrl": "https://example.org/spring-security"
  },
  {
    "id": "remote-2",
    "title": "Remote Java 25",
    "version": "25",
    "tags": ["java", "loom"],
    "content": "Virtual threads improve throughput for IO-bound workloads.",
    "sourceUrl": "https://example.org/java-25"
  }
]
'''.stripIndent().getBytes('UTF-8')
        exchange.responseHeaders.add('Content-Type', 'application/json')
        exchange.sendResponseHeaders(200, body.length)
        exchange.responseBody.write(body)
        exchange.close()
    }

    private void handleMarkdown(HttpExchange exchange) throws IOException {
        byte[] body = '''
# Java MCP Guide

Use virtual threads for concurrent request handling.

## Security

Prefer API key authentication for machine clients.
'''.stripIndent().getBytes('UTF-8')
        exchange.responseHeaders.add('Content-Type', 'text/markdown')
        exchange.sendResponseHeaders(200, body.length)
        exchange.responseBody.write(body)
        exchange.close()
    }
}
