package com.passage.agent.service;

import com.mybatisflex.core.service.IService;
import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import com.passage.agent.agent.run.AgentRunStatus;
import com.passage.agent.model.entity.AgentCheckpointRecord;

/** Persistence boundary for checkpoint writes and compare-and-set recovery claims. */
public interface AgentCheckpointService extends IService<AgentCheckpointRecord> {

    WorkflowCheckpoint persistCheckpoint(
            String runId,
            long expectedStateVersion,
            String checkpointId,
            String nodeId,
            String stateSnapshot,
            AgentRunStatus targetRunStatus);

    WorkflowCheckpoint claimForResume(String checkpointId);

    WorkflowCheckpoint findReadyCheckpoint(String runId, String nodeId);

    void consumeClaim(String checkpointId);

    void releaseClaim(String checkpointId);

    void cancelPendingForRun(String runId);
}
