package com.passage.agent.agent.policy;

/** Exception whose message intentionally excludes request headers, tokens and response bodies. */
public class ToolPolicyException extends RuntimeException {
    private final ToolPolicyError error;

    public ToolPolicyException(ToolPolicyError error, String message) {
        super(message);
        this.error = error;
    }

    public ToolPolicyException(ToolPolicyError error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    public ToolPolicyError error() {
        return error;
    }
}
