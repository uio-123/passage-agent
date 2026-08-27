package com.yupi.template.agent.supervisor;

/** A bounded unit of work produced by a supervisor plan. */
public record SubtaskSpec(int sectionIndex, String id, String instruction) {

    public SubtaskSpec {
        if (sectionIndex < 0) {
            throw new IllegalArgumentException("sectionIndex must not be negative");
        }
    }
}
