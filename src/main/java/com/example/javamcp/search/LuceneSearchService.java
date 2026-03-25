package com.example.javamcp.search;

import com.example.javamcp.model.IngestedDocument;
import com.example.javamcp.model.SearchDiagnostics;
import com.example.javamcp.model.SearchResponse;
import com.example.javamcp.model.SearchResult;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
public class LuceneSearchService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final EmbeddingService embeddingService;
    private final QueryExpansionService queryExpansionService;
    private final SearchProperties searchProperties;
    private final ExecutorService virtualThreadExecutorService;
    private final Path indexPath;
    private final Analyzer analyzer;
    private final AtomicReference<SearchRuntime> searchRuntime = new AtomicReference<>();

    public LuceneSearchService(LuceneProperties luceneProperties,
                               EmbeddingService embeddingService,
                               QueryExpansionService queryExpansionService,
                               SearchProperties searchProperties,
                               ExecutorService virtualThreadExecutorService) {
        this.embeddingService = embeddingService;
        this.queryExpansionService = queryExpansionService;
        this.searchProperties = searchProperties;
        this.virtualThreadExecutorService = virtualThreadExecutorService;
        this.indexPath = Path.of(luceneProperties.indexPath());
        this.analyzer = new StandardAnalyzer();
    }

    @CacheEvict(value = "searchResults", allEntries = true)
    public synchronized void rebuildIndex(List<IngestedDocument> documents) {
        try {
            Files.createDirectories(indexPath);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create Lucene index directory", e);
        }

        IndexWriterConfig writerConfig = new IndexWriterConfig(new StandardAnalyzer())
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        try (Directory directory = FSDirectory.open(indexPath);
             IndexWriter writer = new IndexWriter(directory, writerConfig)) {
            for (IngestedDocument source : documents) {
                String normalizedId = normalize(source.id());
                String normalizedTitle = normalize(source.title());
                String normalizedContent = normalize(source.content());
                String normalizedVersion = normalize(source.version());
                String normalizedSource = normalize(source.source());
                String normalizedSourceKeyword = normalizeKeyword(source.source());

                Document doc = new Document();
                doc.add(new StringField("id", normalizedId, org.apache.lucene.document.Field.Store.YES));
                doc.add(new TextField("title", normalizedTitle, org.apache.lucene.document.Field.Store.YES));
                doc.add(new TextField("content", normalizedContent, org.apache.lucene.document.Field.Store.YES));
                String tags = source.tags() == null ? "" : String.join(" ", source.tags());
                doc.add(new TextField("tags", tags, org.apache.lucene.document.Field.Store.YES));
                if (source.tags() != null) {
                    for (String tag : source.tags()) {
                        String normalizedTag = normalizeKeyword(tag);
                        doc.add(new StringField("tag", normalizedTag, org.apache.lucene.document.Field.Store.NO));
                        doc.add(new StringField("tagStored", normalizedTag, org.apache.lucene.document.Field.Store.YES));
                    }
                }
                doc.add(new StringField("version", normalizedVersion, org.apache.lucene.document.Field.Store.YES));
                doc.add(new StringField("source", normalizedSource, org.apache.lucene.document.Field.Store.YES));
                doc.add(new StringField("sourceKeyword", normalizedSourceKeyword, org.apache.lucene.document.Field.Store.NO));
                doc.add(new StringField("sourceKeywordStored", normalizedSourceKeyword, org.apache.lucene.document.Field.Store.YES));
                doc.add(new StringField("sourceUrl", source.sourceUrl(), org.apache.lucene.document.Field.Store.YES));
                doc.add(new KnnFloatVectorField(
                        "embedding",
                        embeddingService.embed(normalizedTitle + "\n" + normalizedContent),
                        VectorSimilarityFunction.DOT_PRODUCT
                ));
                writer.addDocument(doc);
            }
            writer.commit();
        } catch (IOException e) {
            throw new IllegalStateException("Failed writing Lucene index", e);
        }

        refreshSearchRuntime();
    }

    @Cacheable("searchResults")
    public SearchResponse search(SearchQuery query) {
        long startedAt = System.nanoTime();
        int maxResults = clampLimit(query.limit());
        int candidateCount = Math.max(maxResults, maxResults * searchProperties.getCandidateMultiplier());

        String safeQuery = normalize(query.query());
        String expandedQuery = queryExpansionService.expand(safeQuery);

        SearchRuntime runtime = getOrCreateRuntime();
        if (runtime == null) {
            return buildResponse(safeQuery, expandedQuery, query.mode(), List.of(), 0, 0, startedAt, query.includeDiagnostics());
        }

        IndexSearcher searcher = runtime.acquire();
        try {
            Query filterQuery = buildFilterQuery(query);

            CompletableFuture<ScoreDoc[]> lexicalFuture = CompletableFuture.supplyAsync(
                    () -> runLexicalSafe(searcher, analyzer, safeQuery, expandedQuery, query.mode(), filterQuery, candidateCount),
                    virtualThreadExecutorService
            );
            CompletableFuture<ScoreDoc[]> vectorFuture = CompletableFuture.supplyAsync(
                    () -> runVectorSafe(searcher, expandedQuery, query, candidateCount),
                    virtualThreadExecutorService
            );

            ScoreDoc[] lexicalDocs = lexicalFuture.join();
            ScoreDoc[] vectorDocs = vectorFuture.join();
            List<String> snippetTokens = queryExpansionService.tokenize(safeQuery);

            Map<Integer, Double> fusedScores = new HashMap<>();
            Map<Integer, Float> lexicalScores = new HashMap<>();
            applyRrfScores(fusedScores, lexicalDocs, lexicalWeight(query.mode()));
            applyRrfScores(fusedScores, vectorDocs, vectorWeight(query.mode()));
            Arrays.stream(lexicalDocs).forEach(scoreDoc -> lexicalScores.put(scoreDoc.doc, scoreDoc.score));

            List<SearchResult> results = fusedScores.entrySet()
                    .stream()
                    .map(entry -> toRankedHit(
                            searcher,
                            entry.getKey(),
                            entry.getValue(),
                            lexicalScores.getOrDefault(entry.getKey(), 0F),
                            query.version()
                    ))
                    .sorted(Comparator.comparing(RankedHit::boostedScore, Comparator.reverseOrder()))
                    .limit(maxResults)
                    .map(hit -> toResult(hit.document(), snippetTokens, hit.lexicalScore(), hit.boostedScore()))
                    .toList();

            return buildResponse(
                    safeQuery,
                    expandedQuery,
                    query.mode(),
                    results,
                    lexicalDocs.length,
                    vectorDocs.length,
                    startedAt,
                    query.includeDiagnostics()
            );
        } catch (Exception e) {
            throw new IllegalStateException("Search failed", e);
        } finally {
            runtime.release(searcher);
        }
    }

    @PreDestroy
    public synchronized void close() {
        SearchRuntime runtime = searchRuntime.getAndSet(null);
        closeRuntime(runtime);
        analyzer.close();
    }

    private synchronized void refreshSearchRuntime() {
        SearchRuntime nextRuntime = openSearchRuntime();
        SearchRuntime previousRuntime = searchRuntime.getAndSet(nextRuntime);
        closeRuntime(previousRuntime);
    }

    private SearchRuntime getOrCreateRuntime() {
        SearchRuntime current = searchRuntime.get();
        if (current != null) {
            return current;
        }

        synchronized (this) {
            current = searchRuntime.get();
            if (current != null) {
                return current;
            }

            SearchRuntime created = openSearchRuntime();
            if (created != null) {
                searchRuntime.set(created);
            }
            return created;
        }
    }

    private SearchRuntime openSearchRuntime() {
        if (!Files.exists(indexPath)) {
            return null;
        }

        Directory directory = null;
        try {
            directory = FSDirectory.open(indexPath);
            SearcherManager manager = new SearcherManager(directory, null);
            return new SearchRuntime(directory, manager);
        } catch (IndexNotFoundException ignored) {
            closeQuietly(directory);
            return null;
        } catch (IOException e) {
            closeQuietly(directory);
            throw new IllegalStateException("Could not open Lucene search runtime", e);
        }
    }

    private void closeRuntime(SearchRuntime runtime) {
        if (runtime == null) {
            return;
        }
        closeQuietly(runtime.searcherManager());
        closeQuietly(runtime.directory());
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Best effort cleanup during runtime refresh/shutdown.
        }
    }

    private SearchResponse buildResponse(String safeQuery,
                                         String expandedQuery,
                                         SearchMode mode,
                                         List<SearchResult> results,
                                         int lexicalCandidates,
                                         int vectorCandidates,
                                         long startedAt,
                                         boolean includeDiagnostics) {
        SearchDiagnostics diagnostics = includeDiagnostics
                ? new SearchDiagnostics(
                mode.name(),
                expandedQuery,
                lexicalCandidates,
                vectorCandidates,
                (int) ((System.nanoTime() - startedAt) / 1_000_000)
        ) : null;

        return new SearchResponse(safeQuery, results.size(), results, diagnostics);
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return searchProperties.getDefaultLimit();
        }
        return Math.min(limit, searchProperties.getMaxLimit());
    }

    private ScoreDoc[] runLexical(IndexSearcher searcher,
                                  Analyzer analyzer,
                                  String safeQuery,
                                  String expandedQuery,
                                  SearchMode mode,
                                  Query filterQuery,
                                  int candidateCount) throws Exception {
        if (mode == SearchMode.VECTOR) {
            return new ScoreDoc[0];
        }

        Query lexicalQuery;
        if (safeQuery.isBlank()) {
            lexicalQuery = new MatchAllDocsQuery();
        } else {
            MultiFieldQueryParser parser = new MultiFieldQueryParser(
                    new String[]{"title", "content", "tags"}, analyzer);
            parser.setDefaultOperator(QueryParser.Operator.AND);
            lexicalQuery = parser.parse(QueryParser.escape(expandedQuery));
        }

        Query finalQuery = withFilter(lexicalQuery, filterQuery);
        return searcher.search(finalQuery, candidateCount).scoreDocs;
    }

    private ScoreDoc[] runLexicalSafe(IndexSearcher searcher,
                                      Analyzer analyzer,
                                      String safeQuery,
                                      String expandedQuery,
                                      SearchMode mode,
                                      Query filterQuery,
                                      int candidateCount) {
        try {
            return runLexical(searcher, analyzer, safeQuery, expandedQuery, mode, filterQuery, candidateCount);
        } catch (Exception e) {
            throw new IllegalStateException("Lexical search failed", e);
        }
    }

    private ScoreDoc[] runVector(IndexSearcher searcher,
                                 String expandedQuery,
                                 SearchQuery query,
                                 int candidateCount) throws IOException {
        if (query.mode() == SearchMode.LEXICAL || expandedQuery.isBlank()) {
            return new ScoreDoc[0];
        }

        TopDocs topDocs = searcher.search(
                new KnnFloatVectorQuery("embedding", embeddingService.embed(expandedQuery), candidateCount * 2),
                candidateCount * 2
        );
        if (!hasAnyFilter(query)) {
            return topDocs.scoreDocs;
        }

        List<ScoreDoc> filtered = new ArrayList<>(candidateCount);
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.storedFields().document(scoreDoc.doc);
            if (matchesFilters(doc, query)) {
                filtered.add(scoreDoc);
            }
            if (filtered.size() >= candidateCount) {
                break;
            }
        }
        return filtered.toArray(new ScoreDoc[0]);
    }

    private ScoreDoc[] runVectorSafe(IndexSearcher searcher,
                                     String expandedQuery,
                                     SearchQuery query,
                                     int candidateCount) {
        try {
            return runVector(searcher, expandedQuery, query, candidateCount);
        } catch (Exception e) {
            throw new IllegalStateException("Vector search failed", e);
        }
    }

    private Query buildFilterQuery(SearchQuery query) {
        BooleanQuery.Builder filters = new BooleanQuery.Builder();
        boolean hasFilter = false;

        if (query.version() != null && !query.version().isBlank()) {
            filters.add(new TermQuery(new Term("version", query.version().trim())), BooleanClause.Occur.FILTER);
            hasFilter = true;
        }

        if (query.source() != null && !query.source().isBlank()) {
            filters.add(new TermQuery(new Term("sourceKeyword", normalizeKeyword(query.source()))), BooleanClause.Occur.FILTER);
            hasFilter = true;
        }

        for (String tag : query.tags()) {
            if (tag != null && !tag.isBlank()) {
                filters.add(new TermQuery(new Term("tag", normalizeKeyword(tag))), BooleanClause.Occur.FILTER);
                hasFilter = true;
            }
        }

        return hasFilter ? filters.build() : null;
    }

    private boolean hasAnyFilter(SearchQuery query) {
        boolean hasVersion = query.version() != null && !query.version().isBlank();
        boolean hasSource = query.source() != null && !query.source().isBlank();
        boolean hasTag = query.tags().stream().anyMatch(tag -> tag != null && !tag.isBlank());
        return hasVersion || hasSource || hasTag;
    }

    private boolean matchesFilters(Document doc, SearchQuery query) {
        if (query.version() != null && !query.version().isBlank()) {
            if (!query.version().trim().equals(doc.get("version"))) {
                return false;
            }
        }

        if (query.source() != null && !query.source().isBlank()) {
            String sourceKeyword = doc.get("sourceKeywordStored");
            if (sourceKeyword == null || !sourceKeyword.equals(normalizeKeyword(query.source()))) {
                return false;
            }
        }

        if (!query.tags().isEmpty()) {
            Set<String> storedTags = new HashSet<>();
            for (String storedTag : doc.getValues("tagStored")) {
                if (storedTag != null && !storedTag.isBlank()) {
                    storedTags.add(storedTag);
                }
            }
            for (String requestedTag : query.tags()) {
                String normalizedRequestedTag = normalizeKeyword(requestedTag);
                if (normalizedRequestedTag.isBlank()) {
                    continue;
                }
                if (!storedTags.contains(normalizedRequestedTag)) {
                    return false;
                }
            }
        }

        return true;
    }

    private Query withFilter(Query primary, Query filterQuery) {
        if (filterQuery == null) {
            return primary;
        }

        return new BooleanQuery.Builder()
                .add(primary, BooleanClause.Occur.MUST)
                .add(filterQuery, BooleanClause.Occur.FILTER)
                .build();
    }

    private void applyRrfScores(Map<Integer, Double> scores, ScoreDoc[] scoreDocs, double weight) {
        for (int i = 0; i < scoreDocs.length; i++) {
            ScoreDoc scoreDoc = scoreDocs[i];
            double rrf = weight / (searchProperties.getRrfK() + i + 1.0);
            scores.merge(scoreDoc.doc, rrf, Double::sum);
        }
    }

    private double lexicalWeight(SearchMode mode) {
        if (mode == SearchMode.VECTOR) {
            return 0.0;
        }
        if (mode == SearchMode.LEXICAL) {
            return 1.0;
        }
        return searchProperties.getLexicalWeight();
    }

    private double vectorWeight(SearchMode mode) {
        if (mode == SearchMode.LEXICAL) {
            return 0.0;
        }
        if (mode == SearchMode.VECTOR) {
            return 1.0;
        }
        return searchProperties.getVectorWeight();
    }

    private RankedHit toRankedHit(IndexSearcher searcher,
                                  int docId,
                                  double baseScore,
                                  float lexicalScore,
                                  String expectedVersion) {
        try {
            Document document = searcher.storedFields().document(docId);
            double boosted = baseScore + versionBoost(document.get("version"), expectedVersion);
            return new RankedHit(document, boosted, lexicalScore);
        } catch (IOException e) {
            throw new IllegalStateException("Failed reading search document", e);
        }
    }

    private double versionBoost(String actualVersion, String expectedVersion) {
        if (expectedVersion == null || expectedVersion.isBlank()) {
            return 0.0;
        }
        if (expectedVersion.trim().equals(actualVersion)) {
            return searchProperties.getExactVersionBoost();
        }
        return 0.0;
    }

    private SearchResult toResult(Document doc,
                                  List<String> snippetTokens,
                                  float lexicalScore,
                                  double fusedScore) {
        String content = doc.get("content");
        return new SearchResult(
                doc.get("id"),
                doc.get("title"),
                buildSnippet(content, snippetTokens),
                doc.get("source"),
                doc.get("sourceUrl"),
                doc.get("version"),
                (float) Math.max(fusedScore, lexicalScore)
        );
    }

    private String buildSnippet(String content, List<String> snippetTokens) {
        if (content == null || content.isBlank()) {
            return "";
        }
        if (snippetTokens == null || snippetTokens.isEmpty()) {
            return content.substring(0, Math.min(220, content.length()));
        }

        String lowerContent = content.toLowerCase(Locale.ROOT);

        int firstIndex = -1;
        for (String token : snippetTokens) {
            int index = lowerContent.indexOf(token);
            if (index >= 0 && (firstIndex == -1 || index < firstIndex)) {
                firstIndex = index;
            }
        }

        if (firstIndex < 0) {
            return content.substring(0, Math.min(220, content.length()));
        }

        int start = Math.max(0, firstIndex - 80);
        int end = Math.min(content.length(), firstIndex + 180);
        return content.substring(start, end).trim();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return WHITESPACE.matcher(value).replaceAll(" ").trim();
    }

    private String normalizeKeyword(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }

    private record SearchRuntime(Directory directory, SearcherManager searcherManager) {
        private IndexSearcher acquire() {
            try {
                return searcherManager.acquire();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to acquire Lucene searcher", e);
            }
        }

        private void release(IndexSearcher searcher) {
            if (searcher == null) {
                return;
            }
            try {
                searcherManager.release(searcher);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to release Lucene searcher", e);
            }
        }
    }

    private record RankedHit(Document document, double boostedScore, float lexicalScore) {
    }
}
