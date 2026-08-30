package com.passage.agent.agent.research;

import java.time.Instant;

/** Bounded, traceable source data passed between research and later writing stages. */
public record ResearchSource(
        String sourceId,
        String canonicalUrl,
        String title,
        String publisher,
        Instant fetchedAt,
        String contentHash,
        String summary,
        String query,
        ResearchSourceStatus status
) {
    public static final int MAX_SUMMARY_LENGTH = 4_000;

    public ResearchSource {
        requireText(sourceId, "sourceId");
        requireText(canonicalUrl, "canonicalUrl");
        requireText(title, "title");
        if (fetchedAt == null) {
            throw new IllegalArgumentException("fetchedAt must not be null");
        }
        requireText(contentHash, "contentHash");
        requireText(summary, "summary");
        requireText(query, "query");
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            throw new IllegalArgumentException("summary exceeds maximum length");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
