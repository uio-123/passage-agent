package com.yupi.template.agent.api;

import java.util.Objects;

/** Safe, framework-independent failure information for a workflow operation. */
public record WorkflowError(
        WorkflowErrorCode code,
        String runId,
        WorkflowStage stage,
        String message,
        boolean retryable
) {
    public WorkflowError {
        Objects.requireNonNull(code, "code");
        requireText(runId, "runId");
        Objects.requireNonNull(stage, "stage");
        requireText(message, "message");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
