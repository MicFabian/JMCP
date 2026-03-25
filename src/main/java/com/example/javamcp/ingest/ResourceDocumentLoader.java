package com.example.javamcp.ingest;

import com.example.javamcp.model.IngestedDocument;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Component
public class ResourceDocumentLoader {

    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
    private final ObjectMapper objectMapper;
    private final IngestionProperties ingestionProperties;

    public ResourceDocumentLoader(ObjectMapper objectMapper, IngestionProperties ingestionProperties) {
        this.objectMapper = objectMapper;
        this.ingestionProperties = ingestionProperties;
    }

    public List<IngestedDocument> loadDocuments() {
        try {
            Resource[] resources = resourceResolver.getResources(ingestionProperties.resourcePattern());
            Arrays.sort(resources, Comparator.comparing(Resource::getFilename, Comparator.nullsLast(String::compareTo)));
            List<IngestedDocument> documents = new ArrayList<>(resources.length);
            for (Resource resource : resources) {
                try (InputStream inputStream = resource.getInputStream()) {
                    documents.add(objectMapper.readValue(inputStream, IngestedDocument.class));
                } catch (Exception e) {
                    throw new IllegalStateException("Failed parsing document resource: " + resource.getDescription(), e);
                }
            }
            return List.copyOf(documents);
        } catch (IOException e) {
            throw new IllegalStateException("Failed loading ingested documents", e);
        }
    }
}
