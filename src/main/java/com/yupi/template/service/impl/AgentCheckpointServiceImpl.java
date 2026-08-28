package com.yupi.template.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yupi.template.agent.checkpoint.CheckpointStatus;
import com.yupi.template.agent.checkpoint.WorkflowCheckpoint;
import com.yupi.template.agent.run.AgentRun;
import com.yupi.template.agent.run.AgentRunStatus;
import com.yupi.template.mapper.AgentCheckpointMapper;
import com.yupi.template.model.entity.AgentCheckpointRecord;
import com.yupi.template.model.entity.AgentRunRecord;
import com.yupi.template.service.AgentCheckpointService;
import com.yupi.template.service.AgentRunService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public AgentCheckpointServiceImpl(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
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
        return toDomain(checkpoint);
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
