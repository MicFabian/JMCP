package com.example.javamcp.tools;

import com.example.javamcp.ingest.IngestionService;
import com.example.javamcp.model.IngestedDocument;
import com.example.javamcp.model.LibraryCandidate;
import com.example.javamcp.model.LibraryDoc;
import com.example.javamcp.model.LibraryDocsResponse;
import com.example.javamcp.model.ResolveLibraryResponse;
import com.example.javamcp.model.SearchResponse;
import com.example.javamcp.model.SearchResult;
import com.example.javamcp.observability.OperationObservationService;
import com.example.javamcp.search.LuceneSearchService;
import com.example.javamcp.search.QueryExpansionService;
import com.example.javamcp.search.SearchMode;
import com.example.javamcp.search.SearchQuery;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public class LibraryToolsService {

    private static final int DEFAULT_LIBRARY_LIMIT = 5;
    private static final int MAX_LIBRARY_LIMIT = 20;
    private static final int DEFAULT_DOC_LIMIT = 5;
    private static final int MAX_DOC_LIMIT = 20;
    private static final int DEFAULT_TOKEN_BUDGET = 5_000;
    private static final int MAX_TOKEN_BUDGET = 20_000;
    private static final float DEFAULT_ALPHA = 0.65f;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9-]+");
    private static final Pattern MULTI_DASH = Pattern.compile("-{2,}");
    private static final Pattern EDGE_DASH = Pattern.compile("(^-|-$)");

    private final IngestionService ingestionService;
    private final LuceneSearchService luceneSearchService;
    private final QueryExpansionService queryExpansionService;
    private final MeterRegistry meterRegistry;
    private final OperationObservationService operationObservationService;

    public LibraryToolsService(IngestionService ingestionService,
                               LuceneSearchService luceneSearchService,
                               QueryExpansionService queryExpansionService,
                               MeterRegistry meterRegistry,
                               OperationObservationService operationObservationService) {
        this.ingestionService = ingestionService;
        this.luceneSearchService = luceneSearchService;
        this.queryExpansionService = queryExpansionService;
        this.meterRegistry = meterRegistry;
        this.operationObservationService = operationObservationService;
    }

    public ResolveLibraryResponse resolveLibraryId(String libraryName, String topic, Integer limit) {
        return resolveLibraryId(null, libraryName, topic, limit);
    }

    public ResolveLibraryResponse resolveLibraryId(String query,
                                                   String libraryName,
                                                   String topic,
                                                   Integer limit) {
        return withMetrics("resolve-library-id", () -> {
            int maxResults = clamp(limit, DEFAULT_LIBRARY_LIMIT, MAX_LIBRARY_LIMIT);
            String resolvedQuery = buildResolveQuery(query, libraryName, topic);
            List<String> queryTokens = queryExpansionService.tokenize(queryExpansionService.expand(resolvedQuery));
            String requestedLibraryName = normalize(libraryName);

            List<IngestedDocument> documents = ingestionService.loadNormalizedDocuments();
            Map<String, List<IngestedDocument>> docsByLibrary = new LinkedHashMap<>();
            Map<String, String> namesByLibrary = new LinkedHashMap<>();

            for (IngestedDocument document : documents) {
                LibraryRef ref = deriveLibraryRef(document);
                docsByLibrary.computeIfAbsent(ref.id(), ignored -> new ArrayList<>()).add(document);
                namesByLibrary.putIfAbsent(ref.id(), ref.name());
            }

            int maxDocCount = docsByLibrary.values().stream().mapToInt(List::size).max().orElse(1);

            List<LibraryCandidate> libraries = docsByLibrary.entrySet()
                    .stream()
                    .map(entry -> toCandidate(
                            entry.getKey(),
                            namesByLibrary.get(entry.getKey()),
                            entry.getValue(),
                            queryTokens,
                            requestedLibraryName,
                            maxDocCount
                    ))
                    .filter(candidate -> queryTokens.isEmpty() || candidate.score() > 0f)
                    .sorted(Comparator
                            .comparing(LibraryCandidate::score, Comparator.reverseOrder())
                            .thenComparing(LibraryCandidate::documentCount, Comparator.reverseOrder())
                            .thenComparing(LibraryCandidate::name))
                    .limit(maxResults)
                    .toList();

            return new ResolveLibraryResponse(resolvedQuery, libraries.size(), libraries);
        });
    }

    public LibraryDocsResponse getLibraryDocs(String libraryId,
                                              String topic,
                                              Integer tokenBudget,
                                              Integer limit,
                                              String version,
                                              SearchMode mode) {
        return queryDocs(libraryId, topic, tokenBudget, limit, version, mode, null);
    }

    public LibraryDocsResponse queryDocs(String libraryId,
                                         String query,
                                         Integer tokenBudget,
                                         Integer limit,
                                         String version,
                                         SearchMode mode,
                                         Double alpha) {
        return withMetrics("query-docs", () -> {
            String normalizedLibraryId = normalizeLibraryId(libraryId);
            String normalizedQuery = normalize(query);
            int maxResults = clamp(limit, DEFAULT_DOC_LIMIT, MAX_DOC_LIMIT);
            int maxTokens = clamp(tokenBudget, DEFAULT_TOKEN_BUDGET, MAX_TOKEN_BUDGET);
            int maxChars = maxTokens * 4;
            float rerankAlpha = normalizeAlpha(alpha);

            List<IngestedDocument> documents = ingestionService.loadNormalizedDocuments();
            Map<String, IngestedDocument> docsById = documents.stream()
                    .collect(LinkedHashMap::new, (map, doc) -> map.put(doc.id(), doc), LinkedHashMap::putAll);

            List<IngestedDocument> libraryDocs = documents.stream()
                    .filter(doc -> belongsToLibrary(doc, normalizedLibraryId))
                    .filter(doc -> version == null || version.isBlank() || version.trim().equals(doc.version()))
                    .toList();

            if (libraryDocs.isEmpty()) {
                throw new IllegalArgumentException("Unknown libraryId: " + normalizedLibraryId);
            }

            String libraryName = deriveLibraryRef(libraryDocs.getFirst()).name();
            List<RankedDocument> ranked = rankDocuments(
                    normalizedLibraryId,
                    normalizedQuery,
                    version,
                    mode,
                    maxResults,
                    libraryDocs,
                    docsById,
                    rerankAlpha
            );

            List<RankedDocument> limited = ranked.stream().limit(maxResults).toList();
            String context = buildContext(normalizedLibraryId, libraryName, normalizedQuery, limited, maxChars);
            int approxTokens = Math.max(1, (context.length() + 3) / 4);

            List<LibraryDoc> responseDocs = limited.stream()
                    .map(item -> new LibraryDoc(
                            item.document().id(),
                            item.document().title(),
                            item.excerpt(),
                            item.document().source(),
                            item.document().sourceUrl(),
                            item.document().version(),
                            item.score(),
                            item.retrievalScore(),
                            item.rerankScore(),
                            item.matchedTerms()
                    ))
                    .toList();

            return new LibraryDocsResponse(
                    normalizedLibraryId,
                    libraryName,
                    normalizedQuery,
                    "hybrid-rerank-dedup",
                    rerankAlpha,
                    responseDocs.size(),
                    approxTokens,
                    context,
                    responseDocs
            );
        });
    }

    private List<RankedDocument> rankDocuments(String libraryId,
                                               String query,
                                               String version,
                                               SearchMode mode,
                                               int limit,
                                               List<IngestedDocument> libraryDocs,
                                               Map<String, IngestedDocument> docsById,
                                               float alpha) {
        if (query.isBlank()) {
            return libraryDocs.stream()
                    .sorted(Comparator.comparing(IngestedDocument::title))
                    .map(document -> new RankedDocument(
                            document,
                            1.0f,
                            0f,
                            1.0f,
                            excerpt(document.content(), 220),
                            List.of()
                    ))
                    .toList();
        }

        List<String> tokens = queryExpansionService.tokenize(queryExpansionService.expand(query));
        SearchMode effectiveMode = mode == null ? SearchMode.HYBRID : mode;
        int candidateLimit = Math.max(limit * 4, 20);
        SearchResponse searchResponse = luceneSearchService.search(new SearchQuery(
                query,
                candidateLimit,
                version,
                List.of(),
                null,
                effectiveMode,
                false
        ));

        List<SearchCandidate> searchCandidates = searchResponse.results().stream()
                .map(result -> toCandidate(result, docsById))
                .filter(candidate -> candidate != null && belongsToLibrary(candidate.document(), libraryId))
                .toList();

        if (!searchCandidates.isEmpty()) {
            float maxRetrieval = searchCandidates.stream()
                    .map(SearchCandidate::retrievalRawScore)
                    .max(Float::compareTo)
                    .orElse(1.0f);

            List<RankedDocument> ranked = searchCandidates.stream()
                    .map(candidate -> toRanked(candidate, tokens, alpha, maxRetrieval))
                    .toList();
            return deduplicateAndSort(ranked);
        }

        List<RankedDocument> fallback = libraryDocs.stream()
                .map(document -> {
                    List<String> matchedTerms = matchedTerms(tokens, document);
                    float overlap = scoreAgainstTokens(toHaystack(document), tokens);
                    return new RankedDocument(
                            document,
                            overlap,
                            0f,
                            overlap,
                            excerpt(document.content(), 220),
                            matchedTerms
                    );
                })
                .filter(document -> document.rerankScore() > 0f)
                .toList();

        return deduplicateAndSort(fallback);
    }

    private RankedDocument toRanked(SearchCandidate candidate, List<String> tokens, float alpha, float maxRetrieval) {
        List<String> matchedTerms = matchedTerms(tokens, candidate.document());
        float overlap = scoreAgainstTokens(toHaystack(candidate.document()), tokens);
        float retrievalNormalized = maxRetrieval <= 0f ? 0f : candidate.retrievalRawScore() / maxRetrieval;
        float rerank = alpha * retrievalNormalized + (1.0f - alpha) * overlap;
        return new RankedDocument(
                candidate.document(),
                rerank,
                retrievalNormalized,
                rerank,
                candidate.excerpt(),
                matchedTerms
        );
    }

    private SearchCandidate toCandidate(SearchResult result, Map<String, IngestedDocument> docsById) {
        IngestedDocument document = docsById.get(result.id());
        if (document == null) {
            return null;
        }
        String excerpt = normalize(result.snippet()).isBlank() ? excerpt(document.content(), 220) : result.snippet();
        return new SearchCandidate(document, Math.max(result.score(), 0f), excerpt);
    }

    private List<RankedDocument> deduplicateAndSort(List<RankedDocument> rankedDocuments) {
        List<RankedDocument> sorted = rankedDocuments.stream()
                .sorted(Comparator
                        .comparing(RankedDocument::rerankScore, Comparator.reverseOrder())
                        .thenComparing(RankedDocument::retrievalScore, Comparator.reverseOrder())
                        .thenComparing(candidate -> candidate.document().title()))
                .toList();

        Set<String> seenIds = new LinkedHashSet<>();
        Set<String> seenSourceUrls = new LinkedHashSet<>();
        List<RankedDocument> deduped = new ArrayList<>(sorted.size());
        for (RankedDocument candidate : sorted) {
            if (!seenIds.add(candidate.document().id())) {
                continue;
            }
            String sourceKey = normalize(candidate.document().sourceUrl()).toLowerCase(Locale.ROOT);
            if (!sourceKey.isBlank() && seenSourceUrls.contains(sourceKey)) {
                continue;
            }
            if (!sourceKey.isBlank()) {
                seenSourceUrls.add(sourceKey);
            }
            deduped.add(candidate);
        }
        return deduped;
    }

    private boolean belongsToLibrary(IngestedDocument document, String expectedLibraryId) {
        return deriveLibraryRef(document).id().equals(expectedLibraryId);
    }

    private LibraryCandidate toCandidate(String libraryId,
                                         String libraryName,
                                         List<IngestedDocument> documents,
                                         List<String> queryTokens,
                                         String requestedLibraryName,
                                         int maxDocCount) {
        Set<String> versions = new LinkedHashSet<>();
        Set<String> sources = new LinkedHashSet<>();
        StringBuilder haystack = new StringBuilder(libraryName).append(' ').append(libraryId).append(' ');

        for (IngestedDocument document : documents) {
            versions.add(document.version());
            sources.add(document.source());
            haystack.append(document.title()).append(' ')
                    .append(document.source()).append(' ')
                    .append(String.join(" ", document.tags())).append(' ');
        }

        float textual = scoreAgainstTokens(haystack.toString(), queryTokens);
        float popularity = documents.size() / (float) maxDocCount;
        float nameBoost = 0f;
        if (!requestedLibraryName.isBlank()) {
            String normalizedName = libraryName.toLowerCase(Locale.ROOT);
            String normalizedRequested = requestedLibraryName.toLowerCase(Locale.ROOT);
            if (normalizedName.contains(normalizedRequested) || normalizedRequested.contains(normalizedName)) {
                nameBoost = 0.25f;
            }
        }

        float baseScore = queryTokens.isEmpty() ? popularity : (textual * 0.75f + popularity * 0.15f + nameBoost * 0.10f);
        float score = Math.max(0f, Math.min(baseScore, 1.0f));

        String summary = documents.stream()
                .map(IngestedDocument::title)
                .findFirst()
                .orElse(libraryName);

        return new LibraryCandidate(
                libraryId,
                libraryName,
                summary,
                documents.size(),
                versions.stream().sorted().toList(),
                sources.stream().sorted().toList(),
                score
        );
    }

    private List<String> matchedTerms(List<String> tokens, IngestedDocument document) {
        if (tokens.isEmpty()) {
            return List.of();
        }

        String haystack = toHaystack(document).toLowerCase(Locale.ROOT);
        Set<String> matched = new LinkedHashSet<>();
        for (String token : tokens) {
            String normalized = token.toLowerCase(Locale.ROOT);
            if (!normalized.isBlank() && haystack.contains(normalized)) {
                matched.add(normalized);
            }
        }
        return matched.stream().limit(8).toList();
    }

    private String toHaystack(IngestedDocument document) {
        return document.title() + " " + document.content() + " " + String.join(" ", document.tags()) + " " + document.source();
    }

    private float scoreAgainstTokens(String text, List<String> tokens) {
        if (tokens.isEmpty()) {
            return 0f;
        }
        String normalizedText = normalize(text).toLowerCase(Locale.ROOT);
        int matches = 0;
        for (String token : tokens) {
            if (normalizedText.contains(token.toLowerCase(Locale.ROOT))) {
                matches++;
            }
        }
        return matches / (float) tokens.size();
    }

    private String buildContext(String libraryId,
                                String libraryName,
                                String topic,
                                List<RankedDocument> documents,
                                int maxChars) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(libraryName).append(" (").append(libraryId).append(")\n\n");
        if (!normalize(topic).isBlank()) {
            builder.append("Query: ").append(normalize(topic)).append("\n\n");
        }

        for (RankedDocument item : documents) {
            String section = "## " + item.document().title() + "\n"
                    + "Source: " + item.document().sourceUrl() + "\n"
                    + "Version: " + item.document().version() + "\n"
                    + "Rerank Score: " + item.rerankScore() + "\n\n"
                    + item.document().content() + "\n\n";

            if (builder.length() + section.length() <= maxChars) {
                builder.append(section);
                continue;
            }

            int remaining = maxChars - builder.length();
            if (remaining > 120) {
                builder.append(section, 0, remaining).append("\n");
            }
            break;
        }
        return builder.toString().trim();
    }

    private LibraryRef deriveLibraryRef(IngestedDocument document) {
        String source = normalize(document.source());
        String sourceUrl = normalize(document.sourceUrl());
        String normalizedSource = source.toLowerCase(Locale.ROOT);

        if (normalizedSource.contains("spring security")) {
            return new LibraryRef("/spring-projects/spring-security", "Spring Security");
        }
        if (normalizedSource.contains("spring framework")) {
            return new LibraryRef("/spring-projects/spring-framework", "Spring Framework");
        }
        if (normalizedSource.contains("spring boot")) {
            return new LibraryRef("/spring-projects/spring-boot", "Spring Boot");
        }
        if (normalizedSource.contains("openjdk") || sourceUrl.contains("openjdk.org")) {
            return new LibraryRef("/openjdk/jdk", "OpenJDK");
        }

        URI uri = safeUri(sourceUrl);
        if (uri != null && uri.getHost() != null) {
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String[] parts = normalize(uri.getPath()).split("/");

            if (host.contains("github.com") && parts.length >= 3) {
                return new LibraryRef("/" + slug(parts[1]) + "/" + slug(parts[2]), titleize(parts[2]));
            }

            String hostSlug = slug(host.replace(".", "-"));
            String sourceSlug = slug(source.isBlank() ? document.id() : source);
            return new LibraryRef("/" + hostSlug + "/" + sourceSlug, source.isBlank() ? titleize(sourceSlug) : source);
        }

        String fallbackSlug = slug(source.isBlank() ? document.id() : source);
        return new LibraryRef("/docs/" + fallbackSlug, source.isBlank() ? titleize(fallbackSlug) : source);
    }

    private URI safeUri(String sourceUrl) {
        try {
            return sourceUrl.isBlank() ? null : URI.create(sourceUrl);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildResolveQuery(String query, String libraryName, String topic) {
        String normalizedQuery = normalize(query);
        if (!normalizedQuery.isBlank()) {
            return normalizedQuery;
        }
        return (normalize(libraryName) + " " + normalize(topic)).trim();
    }

    private String normalizeLibraryId(String libraryId) {
        String normalized = normalize(libraryId).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("libraryId must not be blank");
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private int clamp(Integer value, int defaultValue, int max) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return Math.min(value, max);
    }

    private float normalizeAlpha(Double alpha) {
        if (alpha == null) {
            return DEFAULT_ALPHA;
        }
        if (alpha < 0.0d) {
            return 0.0f;
        }
        if (alpha > 1.0d) {
            return 1.0f;
        }
        return alpha.floatValue();
    }

    private String excerpt(String content, int maxLen) {
        String normalized = normalize(content);
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen).trim();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return WHITESPACE.matcher(value).replaceAll(" ").trim();
    }

    private String slug(String value) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        normalized = NON_SLUG.matcher(normalized).replaceAll("-");
        normalized = MULTI_DASH.matcher(normalized).replaceAll("-");
        normalized = EDGE_DASH.matcher(normalized).replaceAll("");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private String titleize(String value) {
        String normalized = normalize(value).replace('-', ' ');
        if (normalized.isBlank()) {
            return "Unknown";
        }
        String[] parts = normalized.split("\\s+");
        StringBuilder title = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!title.isEmpty()) {
                title.append(' ');
            }
            title.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                title.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return title.toString();
    }

    private <T> T withMetrics(String toolName, Supplier<T> supplier) {
        return operationObservationService.observe(
                "jmcp.tool.operation",
                toolName,
                Map.of("mcp.method.name", toolName),
                () -> {
                    long started = System.nanoTime();
                    String status = "ok";
                    try {
                        return supplier.get();
                    } catch (RuntimeException e) {
                        status = "error";
                        throw e;
                    } finally {
                        meterRegistry.counter("mcp.server.tool.calls", "mcp.method.name", toolName, "mcp.status", status)
                                .increment();
                        Timer.builder("mcp.server.operation.duration")
                                .tag("mcp.method.name", toolName)
                                .tag("mcp.status", status)
                                .register(meterRegistry)
                                .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
                    }
                }
        );
    }

    private record LibraryRef(String id, String name) {
    }

    private record SearchCandidate(IngestedDocument document, float retrievalRawScore, String excerpt) {
    }

    private record RankedDocument(
            IngestedDocument document,
            float score,
            float retrievalScore,
            float rerankScore,
            String excerpt,
            List<String> matchedTerms
    ) {
    }
}
