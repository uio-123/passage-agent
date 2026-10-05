package com.passage.agent.agent.tool;

/** Stable agent-facing entry for an authorized tool call. */
public interface ToolExecutor {

    ToolCallResult execute(ToolContext context, ToolId toolId, String input);
}
