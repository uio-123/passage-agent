package com.passage.agent.agent.supervisor;

/** Code-owned next action after a plan or reviewer feedback is evaluated. */
public enum PlanAction {
    CONTINUE,
    REVISE_SECTIONS,
    REQUEST_RESEARCH,
    REPLAN,
    STOP
}
