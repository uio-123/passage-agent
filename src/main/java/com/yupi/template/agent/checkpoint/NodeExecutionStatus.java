package com.yupi.template.agent.checkpoint;

/** The durable result state of one idempotent workflow node attempt. */
public enum NodeExecutionStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED
}
