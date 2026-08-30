package com.passage.agent.agent.tool;

import com.passage.agent.agent.policy.ToolPolicy;

/** A project-owned tool implementation. It is called only by ToolPolicyGateway. */
public interface ToolAdapter {
    ToolId id();

    ToolCallResult execute(ToolCallRequest request, ToolPolicy policy);
}
