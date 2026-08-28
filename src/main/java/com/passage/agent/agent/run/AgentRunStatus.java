package com.passage.agent.agent.run;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle states shared by root article runs and future child-agent runs. */
public enum AgentRunStatus {
    PENDING,
    RUNNING,
    WAITING_FOR_APPROVAL,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean canTransitionTo(AgentRunStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case PENDING -> EnumSet.of(RUNNING, CANCELLED).contains(target);
            case RUNNING -> EnumSet.of(WAITING_FOR_APPROVAL, PAUSED, COMPLETED, FAILED, CANCELLED).contains(target);
            case WAITING_FOR_APPROVAL -> EnumSet.of(RUNNING, CANCELLED).contains(target);
            case PAUSED -> EnumSet.of(RUNNING, CANCELLED).contains(target);
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }

    public boolean terminal() {
        return Set.of(COMPLETED, FAILED, CANCELLED).contains(this);
    }
}
