package com.example.javamcp.ingest;

import com.example.javamcp.model.IngestedDocument;
import com.example.javamcp.model.IngestionSourceStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class RemoteDocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(RemoteDocumentLoader.class);
    private static final int MAX_CHARS_PER_CHUNK = 3_500;
    private static final int MAX_CHUNKS = 20;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Parser markdownParser;
    private final TextContentRenderer markdownTextRenderer;
    private final Map<String, IngestionSourceStatus> statuses = new LinkedHashMap<>();

    public RemoteDocumentLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.markdownParser = Parser.builder()
                .extensions(List.of(TablesExtension.create()))
                .build();
        this.markdownTextRenderer = TextContentRenderer.builder().build();
    }

    public synchronized List<IngestedDocument> loadRemoteDocuments(List<IngestionProperties.RemoteSource> sources) {
        statuses.clear();
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        List<IngestedDocument> allDocuments = new ArrayList<>();
        for (IngestionProperties.RemoteSource source : sources) {
            String sourceId = RemoteSourceIds.normalizedSourceId(source);
            if (!source.isEnabled()) {
                statuses.put(sourceId, new IngestionSourceStatus(
                        sourceId,
                        "remote",
                        source.getUrl(),
                        source.getFormat(),
                        false,
                        0,
                        Instant.now().toString(),
                        null
                ));
                continue;
            }

            try {
                List<IngestedDocument> loaded = fetchAndParse(source, sourceId);
                allDocuments.addAll(loaded);
                statuses.put(sourceId, new IngestionSourceStatus(
                        sourceId,
                        "remote",
                        source.getUrl(),
                        source.getFormat().toUpperCase(Locale.ROOT),
                        true,
                        loaded.size(),
                        Instant.now().toString(),
                        null
                ));
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                statuses.put(sourceId, new IngestionSourceStatus(
                        sourceId,
                        "remote",
                        source.getUrl(),
                        source.getFormat().toUpperCase(Locale.ROOT),
                        true,
                        0,
                        Instant.now().toString(),
                        message
                ));
                if (source.isFailOnError()) {
                    throw new IllegalStateException("Remote ingestion failed for source '" + sourceId + "'", e);
                }
                log.warn("Remote ingestion skipped for source '{}' due to error: {}", sourceId, message);
            }
        }
        return List.copyOf(allDocuments);
    }

    public synchronized List<IngestionSourceStatus> currentStatuses(List<IngestionProperties.RemoteSource> configuredSources) {
        if (configuredSources == null || configuredSources.isEmpty()) {
            return List.of();
        }

        List<IngestionSourceStatus> result = new ArrayList<>(configuredSources.size());
        for (IngestionProperties.RemoteSource source : configuredSources) {
            String sourceId = RemoteSourceIds.normalizedSourceId(source);
            IngestionSourceStatus status = statuses.get(sourceId);
            if (status != null) {
                result.add(status);
                continue;
            }
            result.add(new IngestionSourceStatus(
                    sourceId,
                    "remote",
                    source.getUrl(),
                    source.getFormat().toUpperCase(Locale.ROOT),
                    source.isEnabled(),
                    0,
                    null,
                    null
            ));
        }
        return List.copyOf(result);
    }

    private List<IngestedDocument> fetchAndParse(IngestionProperties.RemoteSource source, String sourceId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(source.getUrl()))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json,text/html,text/plain,text/markdown;q=0.9,*/*;q=0.8")
                .header("User-Agent", "JMCP-RemoteIngest/1.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " while fetching " + source.getUrl());
        }

        String body = response.body() == null ? "" : response.body();
        if (body.isBlank()) {
            return List.of();
        }

        ContentFormat format = resolveFormat(source.getFormat(), source.getUrl(), response.headers().firstValue("Content-Type").orElse(""));
        return switch (format) {
            case AUTO -> parseText(source, sourceId, body);
            case JSON -> parseJson(source, sourceId, body);
            case HTML -> parseHtml(source, sourceId, body);
            case MARKDOWN -> parseMarkdown(source, sourceId, body);
            case TEXT -> parseText(source, sourceId, body);
        };
    }

    private List<IngestedDocument> parseJson(IngestionProperties.RemoteSource source, String sourceId, String body) throws Exception {
        JsonNode node = objectMapper.readTree(body);
        if (node == null || node.isNull()) {
            return List.of();
        }

        List<IngestedDocument> rawDocs = new ArrayList<>();
        if (node.isArray()) {
            int index = 0;
            for (JsonNode item : node) {
                index++;
                IngestedDocument doc = toDocFromJson(source, sourceId, item, index);
                if (doc != null) {
                    rawDocs.add(doc);
                }
            }
        } else if (node.isObject() && node.has("documents") && node.get("documents").isArray()) {
            int index = 0;
            for (JsonNode item : node.get("documents")) {
                index++;
                IngestedDocument doc = toDocFromJson(source, sourceId, item, index);
                if (doc != null) {
                    rawDocs.add(doc);
                }
            }
        } else {
            IngestedDocument doc = toDocFromJson(source, sourceId, node, 1);
            if (doc != null) {
                rawDocs.add(doc);
            }
        }
        return chunkDocuments(rawDocs);
    }

    private IngestedDocument toDocFromJson(IngestionProperties.RemoteSource source,
                                           String sourceId,
                                           JsonNode node,
                                           int index) {
        if (node == null || node.isNull()) {
            return null;
        }

        String content = text(node, "content");
        if (content.isBlank()) {
            content = text(node, "body");
        }
        if (content.isBlank()) {
            content = text(node, "text");
        }
        if (content.isBlank()) {
            return null;
        }

        String id = text(node, "id");
        if (id.isBlank()) {
            id = sourceId + "-doc-" + index;
        }
        String title = text(node, "title");
        if (title.isBlank()) {
            title = source.getSourceName() + " document " + index;
        }
        String version = text(node, "version");
        if (version.isBlank()) {
            version = source.getVersion();
        }
        String sourceUrl = text(node, "sourceUrl");
        if (sourceUrl.isBlank()) {
            sourceUrl = source.getUrl();
        }

        List<String> tags = new ArrayList<>();
        if (!source.getSourceTag().isBlank()) {
            tags.add(source.getSourceTag());
        }
        if (node.has("tags") && node.get("tags").isArray()) {
            for (JsonNode tagNode : node.get("tags")) {
                String tag = stringValue(tagNode);
                if (!tag.isBlank()) {
                    tags.add(tag);
                }
            }
        }

        return new IngestedDocument(
                id,
                title,
                version,
                List.copyOf(tags),
                content,
                source.getSourceName(),
                sourceUrl
        );
    }

    private List<IngestedDocument> parseHtml(IngestionProperties.RemoteSource source, String sourceId, String body) {
        org.jsoup.nodes.Document html = Jsoup.parse(body, source.getUrl());
        String title = html.title();
        if (title == null || title.isBlank()) {
            title = source.getSourceName();
        }
        String content = html.body() == null ? html.text() : html.body().text();
        IngestedDocument raw = new IngestedDocument(
                sourceId,
                title,
                source.getVersion(),
                List.of(source.getSourceTag()),
                content,
                source.getSourceName(),
                source.getUrl()
        );
        return chunkDocuments(List.of(raw));
    }

    private List<IngestedDocument> parseMarkdown(IngestionProperties.RemoteSource source, String sourceId, String body) {
        Node parsed = markdownParser.parse(body);
        String content = markdownTextRenderer.render(parsed);
        String title = firstMarkdownHeading(body);
        if (title.isBlank()) {
            title = source.getSourceName();
        }

        IngestedDocument raw = new IngestedDocument(
                sourceId,
                title,
                source.getVersion(),
                List.of(source.getSourceTag()),
                content,
                source.getSourceName(),
                source.getUrl()
        );
        return chunkDocuments(List.of(raw));
    }

    private List<IngestedDocument> parseText(IngestionProperties.RemoteSource source, String sourceId, String body) {
        IngestedDocument raw = new IngestedDocument(
                sourceId,
                source.getSourceName(),
                source.getVersion(),
                List.of(source.getSourceTag()),
                body,
                source.getSourceName(),
                source.getUrl()
        );
        return chunkDocuments(List.of(raw));
    }

    private List<IngestedDocument> chunkDocuments(List<IngestedDocument> documents) {
        List<IngestedDocument> chunked = new ArrayList<>();
        for (IngestedDocument document : documents) {
            String content = document.content() == null ? "" : document.content().trim();
            if (content.length() <= MAX_CHARS_PER_CHUNK) {
                chunked.add(document);
                continue;
            }

            List<String> chunks = splitContent(content, MAX_CHARS_PER_CHUNK, MAX_CHUNKS);
            for (int i = 0; i < chunks.size(); i++) {
                String id = document.id() + "-chunk-" + (i + 1);
                String title = document.title() + " (chunk " + (i + 1) + "/" + chunks.size() + ")";
                chunked.add(new IngestedDocument(
                        id,
                        title,
                        document.version(),
                        document.tags(),
                        chunks.get(i),
                        document.source(),
                        document.sourceUrl()
                ));
            }
        }
        return List.copyOf(chunked);
    }

    private List<String> splitContent(String content, int maxChars, int maxChunks) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < content.length() && chunks.size() < maxChunks) {
            int end = Math.min(content.length(), start + maxChars);
            if (end < content.length()) {
                int boundary = content.lastIndexOf('\n', end);
                if (boundary > start + maxChars / 2) {
                    end = boundary;
                }
            }
            String chunk = content.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            start = end;
            while (start < content.length() && Character.isWhitespace(content.charAt(start))) {
                start++;
            }
        }
        if (chunks.isEmpty()) {
            return List.of(content);
        }
        return chunks;
    }

    private String firstMarkdownHeading(String markdown) {
        String[] lines = markdown.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                return trimmed.replaceFirst("^#+\\s*", "").trim();
            }
        }
        return "";
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return stringValue(node.get(field)).trim();
    }

    private String stringValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        String raw = node.toString();
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private ContentFormat resolveFormat(String configuredFormat, String url, String contentTypeHeader) {
        ContentFormat configured = ContentFormat.from(configuredFormat);
        if (configured != ContentFormat.AUTO) {
            return configured;
        }

        String contentType = contentTypeHeader == null ? "" : contentTypeHeader.toLowerCase(Locale.ROOT);
        String lowerUrl = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (contentType.contains("application/json") || lowerUrl.endsWith(".json")) {
            return ContentFormat.JSON;
        }
        if (contentType.contains("text/html") || lowerUrl.endsWith(".html") || lowerUrl.endsWith(".htm")) {
            return ContentFormat.HTML;
        }
        if (contentType.contains("markdown") || lowerUrl.endsWith(".md") || lowerUrl.endsWith(".markdown")) {
            return ContentFormat.MARKDOWN;
        }
        return ContentFormat.TEXT;
    }

    private enum ContentFormat {
        AUTO,
        JSON,
        HTML,
        MARKDOWN,
        TEXT;

        private static final Set<String> JSON_ALIASES = Set.of("JSON", "APPLICATION_JSON");
        private static final Set<String> HTML_ALIASES = Set.of("HTML", "TEXT_HTML");
        private static final Set<String> MARKDOWN_ALIASES = Set.of("MARKDOWN", "MD", "TEXT_MARKDOWN");
        private static final Set<String> TEXT_ALIASES = Set.of("TEXT", "PLAIN", "TEXT_PLAIN");

        static ContentFormat from(String raw) {
            if (raw == null || raw.isBlank()) {
                return AUTO;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('/', '_');
            if ("AUTO".equals(normalized)) {
                return AUTO;
            }
            if (JSON_ALIASES.contains(normalized)) {
                return JSON;
            }
            if (HTML_ALIASES.contains(normalized)) {
                return HTML;
            }
            if (MARKDOWN_ALIASES.contains(normalized)) {
                return MARKDOWN;
            }
            if (TEXT_ALIASES.contains(normalized)) {
                return TEXT;
            }
            return AUTO;
        }
    }
}
