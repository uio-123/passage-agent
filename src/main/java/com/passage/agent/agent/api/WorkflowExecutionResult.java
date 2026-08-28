package com.passage.agent.agent.api;

import com.passage.agent.agent.state.WorkflowState;

import java.util.Objects;

/** Typed result returned at every workflow application boundary. */
public record WorkflowExecutionResult(WorkflowState state, WorkflowStage stage) {

    public WorkflowExecutionResult {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(stage, "stage");
    }
}
