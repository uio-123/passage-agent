package com.passage.agent.agent.event;

/** Stable project-owned event names. Framework-specific events never cross this boundary. */
public enum AgentEventType {
    RUN_STARTED, NODE_STARTED, NODE_COMPLETED, NODE_RETRYING, TOOL_CALLED,
    REVIEW_COMPLETED, CHECKPOINT_READY, HITL_REQUIRED, RUN_FAILED, RUN_COMPLETED
}
