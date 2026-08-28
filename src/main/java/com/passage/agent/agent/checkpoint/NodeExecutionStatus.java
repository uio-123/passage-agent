package com.passage.agent.agent.checkpoint;

/** The durable result state of one idempotent workflow node attempt. */
public enum NodeExecutionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED
}
