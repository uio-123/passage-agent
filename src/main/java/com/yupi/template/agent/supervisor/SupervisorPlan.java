package com.yupi.template.agent.supervisor;

import java.util.List;
import java.util.Set;

/** Structured output expected from a supervisor; no free-form routing strings. */
public record SupervisorPlan(
        boolean researchRequired,
        int maxConcurrency,
        int maxSubtasks,
        Set<String> allowedTools,
        List<SubtaskSpec> subtasks
) {
    public SupervisorPlan(boolean researchRequired, int maxConcurrency, List<SubtaskSpec> subtasks) {
        this(researchRequired, maxConcurrency, Math.max(1, subtasks == null ? 0 : subtasks.size()), Set.of(), subtasks);
    }

    public SupervisorPlan {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be at least one");
        }
        if (maxSubtasks < 1) {
            throw new IllegalArgumentException("maxSubtasks must be at least one");
        }
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        subtasks = subtasks == null ? List.of() : List.copyOf(subtasks);
    }
}
