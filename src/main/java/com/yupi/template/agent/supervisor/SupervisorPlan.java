package com.yupi.template.agent.supervisor;

import java.util.List;

/** Structured output expected from a supervisor; no free-form routing strings. */
public record SupervisorPlan(
        boolean researchRequired,
        int maxConcurrency,
        List<SubtaskSpec> subtasks
) {
    public SupervisorPlan {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be at least one");
        }
        subtasks = List.copyOf(subtasks);
    }
}
