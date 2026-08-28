package com.passage.agent.service.impl;

import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import com.passage.agent.service.AgentCheckpointService;
import com.passage.agent.service.AgentRunService;
import com.passage.agent.service.WorkflowRecoveryService;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.function.Consumer;

/** Coordinates durable checkpoint claim/consumption without exposing graph internals. */
@Service
public class WorkflowRecoveryServiceImpl implements WorkflowRecoveryService {
    private final AgentCheckpointService checkpoints;
    private final AgentRunService runs;

    public WorkflowRecoveryServiceImpl(AgentCheckpointService checkpoints, AgentRunService runs) {
        this.checkpoints = checkpoints;
        this.runs = runs;
    }

    @Override
    public void resume(String checkpointId, Consumer<WorkflowCheckpoint> nextNode) {
        Objects.requireNonNull(nextNode, "nextNode");
        WorkflowCheckpoint checkpoint = checkpoints.claimForResume(checkpointId);
        try {
            nextNode.accept(checkpoint);
            checkpoints.consumeClaim(checkpointId);
        } catch (RuntimeException exception) {
            checkpoints.releaseClaim(checkpointId);
            throw exception;
        }
    }

    @Override
    public boolean cancel(String runId) {
        boolean cancelled = runs.cancel(runId);
        if (cancelled) checkpoints.cancelPendingForRun(runId);
        return cancelled;
    }
}
