package com.passage.agent.agent.policy;

import com.passage.agent.agent.tool.ToolId;

import java.time.Duration;

/** Deliberately redacted audit data; persistence is introduced in P2 E3. */
public record ToolCallAuditEvent(
        String runId,
        ToolId toolId,
        String target,
        boolean successful,
        ToolPolicyError error,
        Duration elapsed,
        int retries,
        int responseBytes
) {
    public ToolCallAuditEvent {
        if (runId == null || runId.isBlank() || toolId == null || target == null || target.isBlank() || elapsed == null) {
            throw new IllegalArgumentException("audit event fields must not be blank");
        }
    }
}
