package com.yupi.template.agent.run;

import java.time.Instant;
import java.util.Objects;

/**
 * Framework-neutral run identity and lifecycle contract. Persistence and graph
 * integration are deliberately deferred to later P0-B/P1 work.
 */
public record AgentRun(
        String runId,
        String rootRunId,
        String parentRunId,
        AgentRunStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public AgentRun {
        requireText(runId, "runId");
        requireText(rootRunId, "rootRunId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }

    public static AgentRun root(String runId, Instant now) {
        return new AgentRun(runId, runId, null, AgentRunStatus.PENDING, now, now);
    }

    public static AgentRun child(String runId, AgentRun parent, Instant now) {
        Objects.requireNonNull(parent, "parent");
        return new AgentRun(runId, parent.rootRunId(), parent.runId(), AgentRunStatus.PENDING, now, now);
    }

    public AgentRun transitionTo(AgentRunStatus target, Instant now) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(now, "now");
        if (now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("transition time must not be before updatedAt");
        }
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Cannot transition run from " + status + " to " + target);
        }
        return new AgentRun(runId, rootRunId, parentRunId, target, createdAt, now);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
