package com.passage.agent.agent.context;

import java.time.Instant;
import java.util.List;

/** P1 workflow hand-off contract. P4 operational summaries use ObservabilityContextSnapshot instead. */
public record ContextSnapshot(String runId, String summary, List<String> facts, List<String> decisions,
                              List<String> pendingItems, Instant createdAt) {
    public ContextSnapshot {
        if (runId == null || runId.isBlank() || summary == null || summary.isBlank() || createdAt == null) {
            throw new IllegalArgumentException("Context snapshot requires runId, summary and createdAt");
        }
        facts = facts == null ? List.of() : List.copyOf(facts);
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
        pendingItems = pendingItems == null ? List.of() : List.copyOf(pendingItems);
    }
}
