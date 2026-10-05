package com.passage.agent.agent.supervisor;

import java.util.List;
import java.util.Objects;

/** Deterministic outcome of one plan revision. */
public record ReplanResult(
        PlanAction action,
        SupervisorPlan plan,
        List<String> affectedSectionIds
) {
    public ReplanResult {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(plan, "plan");
        affectedSectionIds = affectedSectionIds == null ? List.of() : List.copyOf(affectedSectionIds);
    }
}
