package com.passage.agent.agent.writing;

import java.util.List;

/** Structured Writer output. Citation IDs are source references, never raw URLs or free-form claims. */
public record SectionDraft(int sectionIndex, String sectionId, String markdown, List<String> citationSourceIds) {
    public SectionDraft {
        if (sectionIndex < 0) throw new IllegalArgumentException("sectionIndex must not be negative");
        requireText(sectionId, "sectionId");
        requireText(markdown, "markdown");
        citationSourceIds = citationSourceIds == null ? List.of() : List.copyOf(citationSourceIds);
        if (citationSourceIds.stream().anyMatch(value -> value == null || value.isBlank())
                || citationSourceIds.size() != citationSourceIds.stream().distinct().count()) {
            throw new IllegalArgumentException("citationSourceIds must be non-blank and unique");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
