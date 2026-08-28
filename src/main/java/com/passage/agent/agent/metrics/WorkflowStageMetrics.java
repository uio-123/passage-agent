package com.passage.agent.agent.metrics;

import com.passage.agent.agent.api.WorkflowErrorCode;
import com.passage.agent.agent.api.WorkflowStage;
import com.passage.agent.agent.run.AgentRunStatus;

import java.time.Duration;
import java.util.Objects;

/** Comparable, in-process measurement for one workflow stage. */
public record WorkflowStageMetrics(
        WorkflowStage stage,
        Duration duration,
        int modelCallCount,
        AgentRunStatus resultStatus,
        WorkflowErrorCode errorCode) {

    public WorkflowStageMetrics {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(resultStatus, "resultStatus");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        if (modelCallCount < 0) {
            throw new IllegalArgumentException("modelCallCount must not be negative");
        }
    }
}
