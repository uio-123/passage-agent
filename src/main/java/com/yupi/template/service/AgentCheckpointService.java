package com.yupi.template.service;

import com.mybatisflex.core.service.IService;
import com.yupi.template.agent.checkpoint.WorkflowCheckpoint;
import com.yupi.template.agent.run.AgentRunStatus;
import com.yupi.template.model.entity.AgentCheckpointRecord;

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

    void consumeClaim(String checkpointId);

    void releaseClaim(String checkpointId);

    void cancelPendingForRun(String runId);
}
