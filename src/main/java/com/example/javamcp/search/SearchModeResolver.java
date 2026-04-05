package com.example.javamcp.search;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SearchModeResolver {

    public SearchMode resolve(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return SearchMode.HYBRID;
        }

        try {
            return SearchMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return SearchMode.HYBRID;
        }
    }
}
