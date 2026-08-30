package com.passage.agent.agent.workflow;

import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import com.passage.agent.agent.state.WorkflowState;
import com.passage.agent.agent.state.WorkflowStateReducer;

/** Restores an accepted P3 content boundary without executing model or artifact code again. */
public final class P3ContentRecoveryAdapter {
    private final P3ContentCheckpointCodec codec;
    public P3ContentRecoveryAdapter(P3ContentCheckpointCodec codec) { this.codec = codec; }
    public WorkflowState restore(WorkflowCheckpoint checkpoint, WorkflowState state) {
        if (checkpoint == null || state == null || !"content-quality-accepted".equals(checkpoint.nodeId())) {
            throw new IllegalArgumentException("Checkpoint is not a P3 accepted-content checkpoint");
        }
        P3ContentCheckpointSnapshot snapshot = snapshot(checkpoint);
        if (!snapshot.runId().equals(state.run().runId()) || snapshot.stateVersion() != checkpoint.stateVersion()) {
            throw new IllegalArgumentException("P3 checkpoint does not match the workflow state");
        }
        return WorkflowStateReducer.withContent(state, snapshot.markdown());
    }

    public P3ContentCheckpointSnapshot snapshot(WorkflowCheckpoint checkpoint) {
        if (checkpoint == null || !"content-quality-accepted".equals(checkpoint.nodeId())) {
            throw new IllegalArgumentException("Checkpoint is not a P3 accepted-content checkpoint");
        }
        P3ContentCheckpointSnapshot snapshot = codec.read(checkpoint.stateSnapshot());
        if (!snapshot.runId().equals(checkpoint.runId()) || snapshot.stateVersion() != checkpoint.stateVersion()) {
            throw new IllegalArgumentException("P3 checkpoint snapshot does not match checkpoint identity");
        }
        return snapshot;
    }
}
