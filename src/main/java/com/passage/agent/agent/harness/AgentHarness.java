package com.passage.agent.agent.harness;

import com.passage.agent.agent.api.WorkflowExecutionResult;
import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;

import java.util.function.Consumer;

/**
 * Stable application entry for one agent task. H1 only unifies existing
 * workflow, recovery and cancellation boundaries; it does not implement
 * planning, replanning or context assembly.
 */
public interface AgentHarness {

    WorkflowExecutionResult run(AgentTask task, Consumer<String> streamHandler);

    void resume(String checkpointId, Consumer<WorkflowCheckpoint> nextNode);

    boolean cancel(String runId);
}
