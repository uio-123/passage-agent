package com.passage.agent.agent.supervisor;

import java.util.Objects;

/** Human-in-the-loop decision mapped into the planner boundary. */
public record HumanDecision(Type type, String instruction) {

    public HumanDecision {
        Objects.requireNonNull(type, "type");
        instruction = instruction == null ? null : instruction.trim();
        if (type == Type.MODIFY && (instruction == null || instruction.isBlank())) {
            throw new IllegalArgumentException("MODIFY requires an instruction");
        }
    }

    public enum Type {
        APPROVE,
        MODIFY,
        REJECT
    }
}
