package com.passage.agent.agent.metrics;

import com.passage.agent.agent.api.WorkflowErrorCode;
import com.passage.agent.agent.run.AgentRunStatus;

import java.util.List;
import java.util.Objects;

/** Snapshot of locally collected workflow measurements. It is not persisted. */
public record WorkflowExecutionMetrics(
        String runId,
        List<WorkflowStageMetrics> stages,
        AgentRunStatus resultStatus,
        WorkflowErrorCode errorCode) {

    public WorkflowExecutionMetrics {
        Objects.requireNonNull(runId, "runId");
        stages = List.copyOf(stages);
        Objects.requireNonNull(resultStatus, "resultStatus");
    }
}
