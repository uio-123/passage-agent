package com.passage.agent.agent.harness;

import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import com.passage.agent.agent.run.AgentRun;
import com.passage.agent.agent.run.AgentRunStatus;
import com.passage.agent.model.entity.AgentRunRecord;

import java.util.function.Consumer;

/**
 * Unified durable-state boundary. Implementations delegate to the existing
 * persistence services rather than maintaining another state store.
 */
public interface StateManager {

    AgentRunRecord createRootRun(String taskId);

    AgentRunRecord createChildRun(String runId, String parentRunId);

    AgentRunRecord loadRunRecord(String runId);

    AgentRun loadRun(String runId);

    void saveRun(AgentRun run, String currentNode);

    void markFailed(String runId, String errorMessage);

    WorkflowCheckpoint createCheckpoint(
            String runId,
            long expectedStateVersion,
            String checkpointId,
            String nodeId,
            String stateSnapshot,
            AgentRunStatus targetRunStatus);

    WorkflowCheckpoint findReadyCheckpoint(String runId, String nodeId);

    void resumeCheckpoint(String checkpointId, Consumer<WorkflowCheckpoint> nextNode);

    boolean cancelRun(String runId);
}
