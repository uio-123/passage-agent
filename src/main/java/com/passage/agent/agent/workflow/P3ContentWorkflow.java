package com.passage.agent.agent.workflow;

/** Narrow port used by the legacy Runner during the opt-in P3 migration. */
@FunctionalInterface
public interface P3ContentWorkflow {
    ApprovedOutlineWritingResult execute(ApprovedOutlineWritingRequest request, int maxConcurrency);
}
