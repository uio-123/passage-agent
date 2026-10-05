package com.passage.agent.agent.supervisor;

/** Planner boundary; implementations must return a verifiable next action and plan version. */
public interface PlanReplanner {

    ReplanResult replan(SupervisorPlan current, PlanFeedback feedback);
}
