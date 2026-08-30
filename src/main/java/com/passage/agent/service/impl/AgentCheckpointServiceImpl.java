package com.passage.agent.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.passage.agent.agent.checkpoint.CheckpointStatus;
import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import com.passage.agent.agent.run.AgentRun;
import com.passage.agent.agent.run.AgentRunStatus;
import com.passage.agent.agent.event.AgentEventInput;
import com.passage.agent.agent.event.AgentEventPublisher;
import com.passage.agent.agent.event.AgentEventType;
import com.passage.agent.agent.context.ContextSnapshotPublisher;
import com.passage.agent.mapper.AgentCheckpointMapper;
import com.passage.agent.model.entity.AgentCheckpointRecord;
import com.passage.agent.model.entity.AgentRunRecord;
import com.passage.agent.service.AgentCheckpointService;
import com.passage.agent.service.AgentRunService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Uses database unique keys plus conditional updates as the recovery lock. No
 * JVM-local lock is used, so competing application instances share the same rule.
 */
@Service
public class AgentCheckpointServiceImpl extends ServiceImpl<AgentCheckpointMapper, AgentCheckpointRecord>
        implements AgentCheckpointService {

    private final AgentRunService agentRunService;
    private final AgentEventPublisher events;
    private final ContextSnapshotPublisher contextSnapshots;

    /** Kept for focused persistence tests; production Spring wiring uses the event-aware constructor. */
    public AgentCheckpointServiceImpl(AgentRunService agentRunService) {
        this(agentRunService, (runId, event) -> { }, (runId,nodeId,stateVersion,checkpointId,targetStatus) -> { });
    }

    @Autowired
    public AgentCheckpointServiceImpl(AgentRunService agentRunService, AgentEventPublisher events, ContextSnapshotPublisher contextSnapshots) {
        this.agentRunService = agentRunService; this.events = events; this.contextSnapshots = contextSnapshots;
    }

    @Override
    @Transactional
    public WorkflowCheckpoint persistCheckpoint(
            String runId, long expectedStateVersion, String checkpointId, String nodeId,
            String stateSnapshot, AgentRunStatus targetRunStatus) {
        AgentRunRecord runRecord = requireRun(runId);
        long currentVersion = runRecord.getStateVersion() == null ? 0L : runRecord.getStateVersion();
        if (currentVersion != expectedStateVersion) {
            throw new IllegalStateException("Stale workflow state version for run: " + runId);
        }
        AgentRun currentRun = agentRunService.getDomain(runId);
        currentRun.transitionTo(targetRunStatus, transitionTime(currentRun.updatedAt()));
        long nextVersion = Math.addExact(expectedStateVersion, 1L);
        LocalDateTime now = LocalDateTime.now();
        AgentCheckpointRecord checkpoint = AgentCheckpointRecord.builder()
                .checkpointId(checkpointId).runId(runId).nodeId(nodeId).stateVersion(nextVersion)
                .stateSnapshot(stateSnapshot).status(CheckpointStatus.READY.name()).createTime(now).build();
        if (!this.save(checkpoint)) {
            throw new IllegalStateException("Unable to persist checkpoint for run: " + runId);
        }
        AgentRunRecord update = AgentRunRecord.builder()
                .checkpointId(checkpointId).stateSnapshot(stateSnapshot).stateVersion(nextVersion)
                .status(targetRunStatus.name()).build();
        boolean advanced = agentRunService.update(update, QueryWrapper.create()
                .eq("runId", runId).eq("stateVersion", expectedStateVersion)
                .eq("status", runRecord.getStatus()));
        if (!advanced) {
            throw new IllegalStateException("Concurrent workflow state update for run: " + runId);
        }
        WorkflowCheckpoint persisted = toDomain(checkpoint);
        events.publish(runId, new AgentEventInput(AgentEventType.CHECKPOINT_READY, nodeId, "workflow", null,
                java.util.Map.of("stateVersion", Long.toString(nextVersion), "targetStatus", targetRunStatus.name())));
        contextSnapshots.checkpointReady(runId,nodeId,nextVersion,checkpointId,targetRunStatus.name());
        return persisted;
    }

    @Override
    @Transactional
    public WorkflowCheckpoint claimForResume(String checkpointId) {
        AgentCheckpointRecord checkpoint = getByCheckpointId(checkpointId);
        if (checkpoint == null) {
            throw new IllegalStateException("Checkpoint does not exist: " + checkpointId);
        }
        LocalDateTime now = LocalDateTime.now();
        AgentCheckpointRecord claim = AgentCheckpointRecord.builder()
                .status(CheckpointStatus.CLAIMED.name()).claimedAt(now).build();
        boolean claimed = this.update(claim, QueryWrapper.create()
                .eq("checkpointId", checkpointId).eq("status", CheckpointStatus.READY.name()));
        if (!claimed) {
            throw new IllegalStateException("Checkpoint is no longer available: " + checkpointId);
        }
        AgentRunRecord run = requireRun(checkpoint.getRunId());
        AgentRunStatus currentStatus = AgentRunStatus.valueOf(run.getStatus());
        if (!currentStatus.canTransitionTo(AgentRunStatus.RUNNING)) {
            throw new IllegalStateException("Run cannot resume from status: " + currentStatus);
        }
        AgentRunRecord runUpdate = AgentRunRecord.builder().status(AgentRunStatus.RUNNING.name()).build();
        boolean resumed = agentRunService.update(runUpdate, QueryWrapper.create()
                .eq("runId", run.getRunId()).eq("stateVersion", checkpoint.getStateVersion())
                .eq("checkpointId", checkpointId).eq("status", run.getStatus()));
        if (!resumed) {
            releaseClaim(checkpointId);
            throw new IllegalStateException("Run changed before checkpoint could resume: " + checkpointId);
        }
        checkpoint.setStatus(CheckpointStatus.CLAIMED.name());
        checkpoint.setClaimedAt(now);
        WorkflowCheckpoint claimedCheckpoint = toDomain(checkpoint);
        events.publish(checkpoint.getRunId(), new AgentEventInput(AgentEventType.NODE_STARTED, checkpoint.getNodeId(), "workflow", 1,
                java.util.Map.of("reason", "checkpoint-resume")));
        return claimedCheckpoint;
    }

    @Override
    public WorkflowCheckpoint findReadyCheckpoint(String runId, String nodeId) {
        AgentCheckpointRecord checkpoint = this.getOne(QueryWrapper.create().eq("runId", runId).eq("nodeId", nodeId)
                .eq("status", CheckpointStatus.READY.name()));
        if (checkpoint == null) {
            throw new IllegalStateException("No ready checkpoint for run and node: " + runId + "/" + nodeId);
        }
        return toDomain(checkpoint);
    }

    @Override
    @Transactional
    public void consumeClaim(String checkpointId) {
        AgentCheckpointRecord update = AgentCheckpointRecord.builder()
                .status(CheckpointStatus.CONSUMED.name()).consumedAt(LocalDateTime.now()).build();
        boolean consumed = this.update(update, QueryWrapper.create()
                .eq("checkpointId", checkpointId).eq("status", CheckpointStatus.CLAIMED.name()));
        if (!consumed) {
            throw new IllegalStateException("Checkpoint is not claimed: " + checkpointId);
        }
    }

    @Override
    @Transactional
    public void releaseClaim(String checkpointId) {
        AgentCheckpointRecord update = AgentCheckpointRecord.builder().status(CheckpointStatus.READY.name()).build();
        this.update(update, QueryWrapper.create().eq("checkpointId", checkpointId)
                .eq("status", CheckpointStatus.CLAIMED.name()));
    }

    @Override
    @Transactional
    public void cancelPendingForRun(String runId) {
        AgentCheckpointRecord update = AgentCheckpointRecord.builder().status(CheckpointStatus.CANCELLED.name()).build();
        this.update(update, QueryWrapper.create().eq("runId", runId)
                .in("status", CheckpointStatus.READY.name(), CheckpointStatus.CLAIMED.name()));
    }

    private AgentCheckpointRecord getByCheckpointId(String checkpointId) {
        return this.getOne(QueryWrapper.create().eq("checkpointId", checkpointId));
    }

    private AgentRunRecord requireRun(String runId) {
        AgentRunRecord run = agentRunService.getByRunId(runId);
        if (run == null) {
            throw new IllegalStateException("Agent run does not exist: " + runId);
        }
        return run;
    }

    private WorkflowCheckpoint toDomain(AgentCheckpointRecord record) {
        LocalDateTime createdAt = record.getCreateTime();
        LocalDateTime updatedAt = record.getUpdateTime() == null ? createdAt : record.getUpdateTime();
        if (createdAt == null) {
            throw new IllegalStateException("Checkpoint has no create time: " + record.getCheckpointId());
        }
        Instant createdInstant = createdAt.atZone(ZoneId.systemDefault()).toInstant();
        Instant updatedInstant = updatedAt.atZone(ZoneId.systemDefault()).toInstant();
        if (updatedInstant.isBefore(createdInstant)) {
            updatedInstant = createdInstant;
        }
        return new WorkflowCheckpoint(record.getCheckpointId(), record.getRunId(), record.getNodeId(),
                record.getStateVersion(), record.getStateSnapshot(), CheckpointStatus.valueOf(record.getStatus()),
                createdInstant, updatedInstant);
    }

    private Instant transitionTime(Instant persistedTime) {
        Instant now = Instant.now();
        return now.isBefore(persistedTime) ? persistedTime : now;
    }
}
