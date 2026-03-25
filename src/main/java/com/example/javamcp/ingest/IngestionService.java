package com.example.javamcp.ingest;

import com.example.javamcp.model.IngestedDocument;
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
    private final AtomicReference<List<IngestedDocument>> cache = new AtomicReference<>();

    public IngestionService(ResourceDocumentLoader resourceDocumentLoader) {
        this.resourceDocumentLoader = resourceDocumentLoader;
    }

    public List<IngestedDocument> loadNormalizedDocuments() {
        List<IngestedDocument> cached = cache.get();
        if (cached != null) {
            return cached;
        }
        return reloadNormalizedDocuments();
    }

    public synchronized List<IngestedDocument> reloadNormalizedDocuments() {
        List<IngestedDocument> loaded = resourceDocumentLoader.loadDocuments();
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
