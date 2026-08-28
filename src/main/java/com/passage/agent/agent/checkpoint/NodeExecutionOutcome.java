package com.passage.agent.agent.checkpoint;

import java.util.Objects;

/** Result of acquiring or reusing an idempotent workflow-node execution. */
public record NodeExecutionOutcome(
        String executionKey,
        boolean reused,
        String resultSnapshot
) {
    public NodeExecutionOutcome {
        Objects.requireNonNull(executionKey, "executionKey");
        Objects.requireNonNull(resultSnapshot, "resultSnapshot");
    }
}
