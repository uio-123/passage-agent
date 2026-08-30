package com.passage.agent.agent.writing;

import java.util.List;

/** A validated, independently executable article section with explicit evidence requirements. */
public record SectionTask(
        int sectionIndex,
        String id,
        String heading,
        String instruction,
        List<String> requiredSourceIds
) {
    public SectionTask {
        if (sectionIndex < 0) throw new IllegalArgumentException("sectionIndex must not be negative");
        requireText(id, "id");
        requireText(heading, "heading");
        requireText(instruction, "instruction");
        requiredSourceIds = requiredSourceIds == null ? List.of() : List.copyOf(requiredSourceIds);
        if (requiredSourceIds.stream().anyMatch(value -> value == null || value.isBlank())
                || requiredSourceIds.size() != requiredSourceIds.stream().distinct().count()) {
            throw new IllegalArgumentException("requiredSourceIds must be non-blank and unique");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
