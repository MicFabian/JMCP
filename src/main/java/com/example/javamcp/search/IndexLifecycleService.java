package com.example.javamcp.search;

import com.example.javamcp.ingest.IngestionService;
import com.example.javamcp.model.IndexStatsResponse;
import com.example.javamcp.model.IngestedDocument;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class IndexLifecycleService {

    private final IngestionService ingestionService;
    private final LuceneSearchService luceneSearchService;
    private final AtomicReference<IndexStatsResponse> latestStats = new AtomicReference<>();

    public IndexLifecycleService(IngestionService ingestionService,
                                 LuceneSearchService luceneSearchService) {
        this.ingestionService = ingestionService;
        this.luceneSearchService = luceneSearchService;
    }

    public synchronized IndexStatsResponse rebuildIndex() {
        ingestionService.invalidateCache();
        List<IngestedDocument> documents = ingestionService.reloadNormalizedDocuments();
        luceneSearchService.rebuildIndex(documents);
        IndexStatsResponse stats = toStats(documents, Instant.now().toString());
        latestStats.set(stats);
        return stats;
    }

    public IndexStatsResponse currentStats() {
        IndexStatsResponse cached = latestStats.get();
        if (cached != null) {
            return cached;
        }

        List<IngestedDocument> documents = ingestionService.loadNormalizedDocuments();
        return toStats(documents, null);
    }

    private IndexStatsResponse toStats(List<IngestedDocument> documents, String lastIndexedAt) {
        List<String> versions = documents.stream()
                .map(IngestedDocument::version)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        List<String> tags = documents.stream()
                .flatMap(doc -> doc.tags().stream())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        List<String> sources = documents.stream()
                .map(IngestedDocument::source)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        return new IndexStatsResponse(documents.size(), versions, tags, sources, lastIndexedAt);
    }
}
