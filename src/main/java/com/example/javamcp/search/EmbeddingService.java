package com.example.javamcp.search;

import org.springframework.stereotype.Service;

import java.text.BreakIterator;
import java.util.Locale;

@Service
public class EmbeddingService {

    public static final int DIMENSION = 64;

    public float[] embed(String text) {
        float[] vector = new float[DIMENSION];
        if (text == null || text.isBlank()) {
            return vector;
        }

        BreakIterator iterator = BreakIterator.getWordInstance(Locale.ENGLISH);
        iterator.setText(text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            if (!Character.isLetterOrDigit(text.charAt(start))) {
                continue;
            }
            String token = text.substring(start, end).toLowerCase(Locale.ROOT);
            int bucket = Math.floorMod(token.hashCode(), DIMENSION);
            vector[bucket] += 1.0f;
        }

        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm == 0) {
            return vector;
        }

        float denominator = (float) Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= denominator;
        }
        return vector;
    }
}
