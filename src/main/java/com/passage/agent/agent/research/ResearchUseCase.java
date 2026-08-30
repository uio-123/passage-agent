package com.passage.agent.agent.research;

/** The only Supervisor-facing boundary for a bounded research child run. */
@FunctionalInterface
public interface ResearchUseCase {
    ResearchBundle execute(String researchRunId, ResearchRequest request);
}
