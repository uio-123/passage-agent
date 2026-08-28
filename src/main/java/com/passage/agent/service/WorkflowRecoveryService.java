package com.passage.agent.service;

import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;

import java.util.function.Consumer;

/** Application boundary for one-time checkpoint recovery and cancellation. */
public interface WorkflowRecoveryService {

    void resume(String checkpointId, Consumer<WorkflowCheckpoint> nextNode);

    boolean cancel(String runId);
}
