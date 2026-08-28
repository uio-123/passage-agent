package com.yupi.template.agent.supervisor;

import java.util.List;
import java.util.Set;

/** A bounded unit of work produced by a supervisor plan. */
public record SubtaskSpec(
        int sectionIndex,
        String id,
        String instruction,
        List<String> dependsOn,
        Set<String> requiredTools
) {

    public SubtaskSpec(int sectionIndex, String id, String instruction) {
        this(sectionIndex, id, instruction, List.of(), Set.of());
    }

    public SubtaskSpec {
        if (sectionIndex < 0) {
            throw new IllegalArgumentException("sectionIndex must not be negative");
        }
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        requiredTools = requiredTools == null ? Set.of() : Set.copyOf(requiredTools);
    }
}
