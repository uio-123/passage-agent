package com.passage.agent.agent.tool;

import java.util.Set;

/** Per-agent authorization and budget for one tool execution boundary. */
public record ToolContext(
        String runId,
        String agentName,
        Set<ToolId> allowedTools,
        int remainingCallBudget
) {
    public ToolContext {
        if (runId == null || runId.isBlank() || agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("tool context identity must not be blank");
        }
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        if (remainingCallBudget < 1) {
            throw new IllegalArgumentException("remainingCallBudget must be positive");
        }
    }
}
