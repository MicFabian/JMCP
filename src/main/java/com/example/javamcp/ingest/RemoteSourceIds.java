package com.example.javamcp.ingest;

import java.util.Locale;
import java.util.regex.Pattern;

public final class RemoteSourceIds {

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern MULTI_DASH = Pattern.compile("-{2,}");

    private RemoteSourceIds() {
    }

    public static String normalizedSourceId(IngestionProperties.RemoteSource source) {
        if (source == null) {
            return "remote-source";
        }

        String configuredId = source.getId();
        if (configuredId != null && !configuredId.isBlank()) {
            return sanitizeId(configuredId);
        }
        return sanitizeId(source.getUrl());
    }

    public static String sanitizeId(String raw) {
        String lower = raw == null ? "remote-source" : raw.toLowerCase(Locale.ROOT);
        String normalized = NON_ALNUM.matcher(lower).replaceAll("-");
        normalized = MULTI_DASH.matcher(normalized).replaceAll("-");
        normalized = normalized.replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "remote-source" : normalized;
    }
}
