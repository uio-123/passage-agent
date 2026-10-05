package com.passage.agent.agent.context;

import java.util.List;
import java.util.Objects;

/** Candidate context before role filtering and budget selection. */
public record ContextAssemblyRequest(
        String runId,
        AgentContext.Role role,
        AgentContext.Budget budget,
        List<AgentContext.Item> candidates
) {
    public ContextAssemblyRequest {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(budget, "budget");
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("context candidates must not contain null");
        }
    }
}
