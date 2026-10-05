package com.passage.agent.agent.harness;

import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import com.passage.agent.agent.run.AgentRun;
import com.passage.agent.agent.run.AgentRunStatus;
import com.passage.agent.model.entity.AgentRunRecord;
import com.passage.agent.service.AgentCheckpointService;
import com.passage.agent.service.AgentRunService;
import com.passage.agent.service.WorkflowRecoveryService;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.function.Consumer;

/** Durable-state facade over the existing Run, Checkpoint and Recovery services. */
@Service
public class DefaultStateManager implements StateManager {

    private final AgentRunService runs;
    private final AgentCheckpointService checkpoints;
    private final WorkflowRecoveryService recovery;

    public DefaultStateManager(
            AgentRunService runs,
            AgentCheckpointService checkpoints,
            WorkflowRecoveryService recovery) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.checkpoints = Objects.requireNonNull(checkpoints, "checkpoints");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
    }

    @Override
    public AgentRunRecord createRootRun(String taskId) {
        return runs.createRootRun(taskId);
    }

    @Override
    public AgentRunRecord createChildRun(String runId, String parentRunId) {
        return runs.createChildRun(runId, parentRunId);
    }

    @Override
    public AgentRunRecord loadRunRecord(String runId) {
        return runs.getByRunId(runId);
    }

    @Override
    public AgentRun loadRun(String runId) {
        return runs.getDomain(runId);
    }

    @Override
    public void saveRun(AgentRun run, String currentNode) {
        runs.sync(run, currentNode);
    }

    @Override
    public void markFailed(String runId, String errorMessage) {
        runs.markFailed(runId, errorMessage);
    }

    @Override
    public WorkflowCheckpoint createCheckpoint(
            String runId,
            long expectedStateVersion,
            String checkpointId,
            String nodeId,
            String stateSnapshot,
            AgentRunStatus targetRunStatus) {
        return checkpoints.persistCheckpoint(
                runId, expectedStateVersion, checkpointId, nodeId, stateSnapshot, targetRunStatus);
    }

    @Override
    public WorkflowCheckpoint findReadyCheckpoint(String runId, String nodeId) {
        return checkpoints.findReadyCheckpoint(runId, nodeId);
    }

    @Override
    public void resumeCheckpoint(String checkpointId, Consumer<WorkflowCheckpoint> nextNode) {
        recovery.resume(checkpointId, nextNode);
    }

    @Override
    public boolean cancelRun(String runId) {
        return recovery.cancel(runId);
    }
}
