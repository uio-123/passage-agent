package com.passage.agent.agent.checkpoint;

import java.time.Instant;
import java.util.Objects;

/** Framework-neutral checkpoint contract; database adapters enforce the CAS claim. */
public record WorkflowCheckpoint(
        String checkpointId,
        String runId,
        String nodeId,
        long stateVersion,
        String stateSnapshot,
        CheckpointStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public WorkflowCheckpoint {
        requireText(checkpointId, "checkpointId");
        requireText(runId, "runId");
        requireText(nodeId, "nodeId");
        requireText(stateSnapshot, "stateSnapshot");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (stateVersion < 0) {
            throw new IllegalArgumentException("stateVersion must not be negative");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public WorkflowCheckpoint transitionTo(CheckpointStatus target, Instant now) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(now, "now");
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("transition time must not be before updatedAt");
        }
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Cannot transition checkpoint from " + status + " to " + target);
        }
        return new WorkflowCheckpoint(checkpointId, runId, nodeId, stateVersion, stateSnapshot, target, createdAt, now);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
