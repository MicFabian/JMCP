package com.example.javamcp.ingest;

import com.example.javamcp.model.IngestedDocument;
import com.example.javamcp.model.IngestionSourceStatus;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ResourceDocumentLoader {

    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
    private final ObjectMapper objectMapper;
    private final IngestionProperties ingestionProperties;
    private final AtomicInteger lastLoadedCount = new AtomicInteger(0);
    private final AtomicReference<String> lastLoadedAt = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();

    public ResourceDocumentLoader(ObjectMapper objectMapper, IngestionProperties ingestionProperties) {
        this.objectMapper = objectMapper;
        this.ingestionProperties = ingestionProperties;
    }

    public List<IngestedDocument> loadClasspathDocuments() {
        if (!ingestionProperties.isIncludeClasspath()) {
            lastLoadedCount.set(0);
            lastLoadedAt.set(Instant.now().toString());
            lastError.set(null);
            return List.of();
        }

        try {
            Resource[] resources = resourceResolver.getResources(ingestionProperties.getResourcePattern());
            Arrays.sort(resources, Comparator.comparing(Resource::getFilename, Comparator.nullsLast(String::compareTo)));
            List<IngestedDocument> documents = new ArrayList<>(resources.length);
            for (Resource resource : resources) {
                try (InputStream inputStream = resource.getInputStream()) {
                    documents.add(objectMapper.readValue(inputStream, IngestedDocument.class));
                } catch (Exception e) {
                    throw new IllegalStateException("Failed parsing document resource: " + resource.getDescription(), e);
                }
            }
            lastLoadedCount.set(documents.size());
            lastLoadedAt.set(Instant.now().toString());
            lastError.set(null);
            return List.copyOf(documents);
        } catch (IOException e) {
            lastLoadedCount.set(0);
            lastLoadedAt.set(Instant.now().toString());
            lastError.set(e.getMessage());
            throw new IllegalStateException("Failed loading ingested documents", e);
        }
    }

    public IngestionSourceStatus classpathStatus() {
        return new IngestionSourceStatus(
                "classpath-docs",
                "classpath",
                ingestionProperties.getResourcePattern(),
                "JSON",
                ingestionProperties.isIncludeClasspath(),
                lastLoadedCount.get(),
                lastLoadedAt.get(),
                lastError.get()
        );
    }
}
