package com.passage.agent.agent.tool;

/** P2 E1 authorization seam; E2 will add network policy, auditing and budget accounting behind it. */
public final class ToolAuthorization {
    private ToolAuthorization() {
    }

    public static void requireAllowed(ToolCallRequest request) {
        if (!request.allowedTools().contains(request.toolId())) {
            throw new IllegalArgumentException("Tool is not authorized for this request: " + request.toolId());
        }
    }
}
