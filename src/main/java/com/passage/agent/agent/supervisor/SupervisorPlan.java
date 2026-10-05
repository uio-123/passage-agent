package com.passage.agent.agent.supervisor;

import java.util.List;
import java.util.Set;

/** Structured output expected from a supervisor; no free-form routing strings. */
public record SupervisorPlan(
        int version,
        Integer parentVersion,
        PlanRevisionReason revisionReason,
        boolean researchRequired,
        int maxConcurrency,
        int maxSubtasks,
        Set<String> allowedTools,
        List<SubtaskSpec> subtasks
) {
    public SupervisorPlan(boolean researchRequired, int maxConcurrency, int maxSubtasks,
                          Set<String> allowedTools, List<SubtaskSpec> subtasks) {
        this(1, null, PlanRevisionReason.INITIAL, researchRequired, maxConcurrency, maxSubtasks, allowedTools, subtasks);
    }

    public SupervisorPlan(boolean researchRequired, int maxConcurrency, List<SubtaskSpec> subtasks) {
        this(researchRequired, maxConcurrency, Math.max(1, subtasks == null ? 0 : subtasks.size()), Set.of(), subtasks);
    }

    public SupervisorPlan {
        if (version < 1) {
            throw new IllegalArgumentException("version must be at least one");
        }
        if (revisionReason == null) {
            throw new IllegalArgumentException("revisionReason must not be null");
        }
        if (version == 1 && (parentVersion != null || revisionReason != PlanRevisionReason.INITIAL)) {
            throw new IllegalArgumentException("initial plan must not have a parent version");
        }
        if (version > 1 && (parentVersion == null || parentVersion >= version
                || revisionReason == PlanRevisionReason.INITIAL)) {
            throw new IllegalArgumentException("revised plan must reference an earlier version");
        }
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
