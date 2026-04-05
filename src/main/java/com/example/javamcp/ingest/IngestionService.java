package com.example.javamcp.ingest;

import com.example.javamcp.model.IngestedDocument;
import com.example.javamcp.model.IngestionSourceStatus;
import com.example.javamcp.observability.OperationObservationService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
public class IngestionService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final ResourceDocumentLoader resourceDocumentLoader;
    private final RemoteDocumentLoader remoteDocumentLoader;
    private final IngestionProperties ingestionProperties;
    private final OperationObservationService operationObservationService;
    private final AtomicReference<List<IngestedDocument>> cache = new AtomicReference<>();

    public IngestionService(ResourceDocumentLoader resourceDocumentLoader,
                            RemoteDocumentLoader remoteDocumentLoader,
                            IngestionProperties ingestionProperties,
                            OperationObservationService operationObservationService) {
        this.resourceDocumentLoader = resourceDocumentLoader;
        this.remoteDocumentLoader = remoteDocumentLoader;
        this.ingestionProperties = ingestionProperties;
        this.operationObservationService = operationObservationService;
    }

    public List<IngestedDocument> loadNormalizedDocuments() {
        List<IngestedDocument> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        return reloadNormalizedDocuments();
    }

    public synchronized List<IngestedDocument> reloadNormalizedDocuments() {
        return operationObservationService.observe(
                "jmcp.ingest.reload",
                "jmcp ingestion reload",
                Map.of("remote.source.count", String.valueOf(ingestionProperties.getRemoteSources().size())),
                this::reloadNormalizedDocumentsInternal
        );
    }

    private synchronized List<IngestedDocument> reloadNormalizedDocumentsInternal() {
        List<IngestedDocument> loaded = new ArrayList<>();
        loaded.addAll(resourceDocumentLoader.loadClasspathDocuments());
        loaded.addAll(remoteDocumentLoader.loadRemoteDocuments(ingestionProperties.getRemoteSources()));
        Map<String, IngestedDocument> uniqueById = new LinkedHashMap<>();
        for (IngestedDocument doc : loaded) {
            IngestedDocument normalized = normalize(doc);
            if (!normalized.id().isBlank()) {
                uniqueById.put(normalized.id(), normalized);
            }
        }
        List<IngestedDocument> normalized = List.copyOf(new ArrayList<>(uniqueById.values()));
        cache.set(normalized);
        return normalized;
    }

    public synchronized void invalidateCache() {
        cache.set(null);
    }

    public List<IngestionSourceStatus> sourceStatuses() {
        List<IngestionSourceStatus> statuses = new ArrayList<>();
        statuses.add(resourceDocumentLoader.classpathStatus());
        statuses.addAll(remoteDocumentLoader.currentStatuses(ingestionProperties.getRemoteSources()));
        return List.copyOf(statuses);
    }

    private IngestedDocument normalize(IngestedDocument doc) {
        List<String> tags = doc.tags() == null
                ? List.of()
                : doc.tags().stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();

        return new IngestedDocument(
                normalize(doc.id()),
                normalize(doc.title()),
                normalize(doc.version()),
                tags,
                normalizeWhitespace(doc.content()),
                normalize(doc.source()),
                normalize(doc.sourceUrl())
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeWhitespace(String value) {
        return WHITESPACE.matcher(normalize(value)).replaceAll(" ");
    }
}
