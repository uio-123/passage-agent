package com.passage.agent.agent.tool;

import java.util.Set;

/** Validated request at the application boundary before a tool implementation is selected. */
public record ToolCallRequest(
        String runId,
        ToolId toolId,
        String input,
        Set<ToolId> allowedTools,
        int remainingCallBudget
) {
    public ToolCallRequest {
        requireText(runId, "runId");
        if (toolId == null) {
            throw new IllegalArgumentException("toolId must not be null");
        }
        requireText(input, "input");
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        if (remainingCallBudget < 1) {
            throw new IllegalArgumentException("remainingCallBudget must be positive");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
