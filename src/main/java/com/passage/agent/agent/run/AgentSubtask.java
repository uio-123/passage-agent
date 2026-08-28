package com.passage.agent.agent.run;

import java.util.List;
import java.util.Objects;

/** A bounded unit of work owned by one parent run. */
public record AgentSubtask(
        String subtaskId,
        String parentRunId,
        int sequence,
        String instruction,
        List<String> allowedTools,
        int tokenBudget,
        String acceptanceCriteria
) {
    public AgentSubtask {
        requireText(subtaskId, "subtaskId");
        requireText(parentRunId, "parentRunId");
        requireText(instruction, "instruction");
        requireText(acceptanceCriteria, "acceptanceCriteria");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        if (tokenBudget < 1) {
            throw new IllegalArgumentException("tokenBudget must be positive");
        }
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
