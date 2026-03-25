package com.example.javamcp.search;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class QueryExpansionService {

    private final SearchProperties searchProperties;

    public QueryExpansionService(SearchProperties searchProperties) {
        this.searchProperties = searchProperties;
    }

    public String expand(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }

        String normalized = query.trim().toLowerCase(Locale.ROOT);
        String[] tokens = normalized.split("\\s+");

        Set<String> expanded = new LinkedHashSet<>();
        var synonymsByToken = searchProperties.getSynonyms() == null ? java.util.Map.<String, List<String>>of() : searchProperties.getSynonyms();
        for (String token : tokens) {
            expanded.add(token);
            List<String> synonyms = synonymsByToken.get(token);
            if (synonyms != null) {
                expanded.addAll(synonyms);
            }
        }

        return String.join(" ", expanded);
    }

    public List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String[] tokens = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        List<String> cleaned = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            if (!token.isBlank()) {
                cleaned.add(token);
            }
        }
        return cleaned;
    }
}
