package com.passage.agent.agent.harness;

import com.passage.agent.agent.state.WorkflowState;

import java.util.Objects;

/** A single application-level action requested from the Agent Harness. */
public record AgentTask(WorkflowState state, Type type) {

    public AgentTask {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(type, "type");
    }

    public enum Type {
        GENERATE_TITLES,
        GENERATE_OUTLINE,
        GENERATE_CONTENT
    }
}
