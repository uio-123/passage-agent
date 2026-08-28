package com.passage.agent.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.passage.agent.agent.run.AgentRun;
import com.passage.agent.agent.run.AgentRunStatus;
import com.passage.agent.mapper.AgentRunMapper;
import com.passage.agent.model.entity.AgentRunRecord;
import com.passage.agent.service.AgentRunService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class AgentRunServiceImpl extends ServiceImpl<AgentRunMapper, AgentRunRecord> implements AgentRunService {

    @Override
    public AgentRunRecord createRootRun(String taskId) {
        LocalDateTime now = LocalDateTime.now();
        AgentRunRecord record = AgentRunRecord.builder()
                .runId(taskId)
                .rootRunId(taskId)
                .taskId(taskId)
                .status(AgentRunStatus.PENDING.name())
                .createTime(now)
                .updateTime(now)
                .build();
        this.save(record);
        return record;
    }

    @Override
    public AgentRunRecord createChildRun(String runId, String parentRunId) {
        AgentRunRecord parent = getByRunId(parentRunId);
        if (parent == null) {
            throw new IllegalStateException("Parent agent run does not exist: " + parentRunId);
        }
        LocalDateTime now = LocalDateTime.now();
        AgentRunRecord record = AgentRunRecord.builder()
                .runId(runId)
                .rootRunId(parent.getRootRunId())
                .parentRunId(parentRunId)
                .taskId(parent.getTaskId())
                .status(AgentRunStatus.PENDING.name())
                .stateVersion(0L)
                .createTime(now)
                .updateTime(now)
                .build();
        this.save(record);
        return record;
    }

    @Override
    public AgentRunRecord getByRunId(String runId) {
        return this.getOne(QueryWrapper.create().eq("runId", runId));
    }

    @Override
    public AgentRun getDomain(String runId) {
        AgentRunRecord record = getByRunId(runId);
        if (record == null) {
            throw new IllegalStateException("Agent run does not exist: " + runId);
        }
        LocalDateTime createdAt = record.getCreateTime();
        LocalDateTime updatedAt = record.getUpdateTime() == null ? createdAt : record.getUpdateTime();
        if (createdAt == null) {
            throw new IllegalStateException("Agent run has no create time: " + runId);
        }
        var createdInstant = createdAt.atZone(ZoneId.systemDefault()).toInstant();
        var updatedInstant = updatedAt.atZone(ZoneId.systemDefault()).toInstant();
        if (updatedInstant.isBefore(createdInstant)) {
            updatedInstant = createdInstant;
        }
        return new AgentRun(
                record.getRunId(), record.getRootRunId(), record.getParentRunId(),
                AgentRunStatus.valueOf(record.getStatus()),
                createdInstant, updatedInstant
        );
    }

    @Override
    public void sync(AgentRun run, String currentNode) {
        AgentRunRecord record = getByRunId(run.runId());
        if (record == null) {
            throw new IllegalStateException("Agent run does not exist: " + run.runId());
        }
        record.setStatus(run.status().name());
        record.setCurrentNode(currentNode);
        this.updateById(record);
    }

    @Override
    public void markFailed(String runId, String errorMessage) {
        AgentRunRecord record = getByRunId(runId);
        if (record == null) {
            return;
        }
        record.setStatus(AgentRunStatus.FAILED.name());
        record.setErrorMessage(errorMessage);
        this.updateById(record);
    }

    @Override
    public boolean cancel(String runId) {
        AgentRunRecord record = getByRunId(runId);
        if (record == null) {
            throw new IllegalStateException("Agent run does not exist: " + runId);
        }
        AgentRunStatus current = AgentRunStatus.valueOf(record.getStatus());
        if (current == AgentRunStatus.CANCELLED) {
            return true;
        }
        if (!current.canTransitionTo(AgentRunStatus.CANCELLED)) {
            return false;
        }
        AgentRunRecord update = AgentRunRecord.builder().status(AgentRunStatus.CANCELLED.name()).build();
        return this.update(update, QueryWrapper.create().eq("runId", runId).eq("status", current.name()));
    }
}
