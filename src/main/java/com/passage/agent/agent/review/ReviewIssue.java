package com.passage.agent.agent.review;

/** A reviewer finding tied to one section; reviewers provide advice, never a rewritten draft. */
public record ReviewIssue(String sectionId, ReviewSeverity severity, String code, String message) {
    public ReviewIssue {
        requireText(sectionId, "sectionId");
        if (severity == null) throw new IllegalArgumentException("severity must not be null");
        requireText(code, "code");
        requireText(message, "message");
    }
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
