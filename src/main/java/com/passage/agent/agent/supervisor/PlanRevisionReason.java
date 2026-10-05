package com.passage.agent.agent.supervisor;

/** Why a plan was created or revised; never inferred from free-form model text. */
public enum PlanRevisionReason {
    INITIAL,
    REVIEW_ACCEPTED,
    REVIEW_REVISION,
    RESEARCH_REQUIRED,
    REVISION_LIMIT,
    HUMAN_APPROVE,
    HUMAN_MODIFY,
    HUMAN_REJECT
}
