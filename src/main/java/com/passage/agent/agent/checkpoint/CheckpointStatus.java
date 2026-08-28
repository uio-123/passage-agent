package com.passage.agent.agent.checkpoint;

/** A checkpoint can be claimed by exactly one resumer before it is consumed. */
public enum CheckpointStatus {
    READY,
    CLAIMED,
    CONSUMED,
    CANCELLED;

    public boolean canTransitionTo(CheckpointStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case READY -> target == CLAIMED || target == CANCELLED;
            case CLAIMED -> target == CONSUMED || target == READY || target == CANCELLED;
            case CONSUMED, CANCELLED -> false;
        };
    }
}
