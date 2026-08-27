package com.yupi.template.agent.metrics;

import com.yupi.template.agent.api.WorkflowErrorCode;
import com.yupi.template.agent.api.WorkflowStage;
import com.yupi.template.agent.run.AgentRunStatus;

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
