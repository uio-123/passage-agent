package com.yupi.template.agent.context;

import java.time.Instant;
import java.util.List;

/** A compact, serializable hand-off record; it is not a long-term memory store. */
public record ContextSnapshot(
        String runId,
        String summary,
        List<String> confirmedFacts,
        List<String> decisions,
        List<String> pendingItems,
        Instant createdAt
) {
    public ContextSnapshot {
        requireText(runId, "runId");
        requireText(summary, "summary");
        confirmedFacts = confirmedFacts == null ? List.of() : List.copyOf(confirmedFacts);
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
        pendingItems = pendingItems == null ? List.of() : List.copyOf(pendingItems);
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
