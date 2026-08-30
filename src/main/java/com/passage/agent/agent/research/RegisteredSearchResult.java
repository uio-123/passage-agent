package com.passage.agent.agent.research;

import java.net.URI;

/** A URL emitted by the registered Search boundary and eligible for Web Reader input. */
public record RegisteredSearchResult(String resultId, String canonicalUrl, String title, String publisher) {
    public RegisteredSearchResult {
        requireText(resultId, "resultId");
        requireText(canonicalUrl, "canonicalUrl");
        requireText(title, "title");
        URI uri = URI.create(canonicalUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("canonicalUrl must be an HTTPS URL");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
