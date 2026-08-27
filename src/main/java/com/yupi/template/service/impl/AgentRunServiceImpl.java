package com.yupi.template.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yupi.template.agent.run.AgentRun;
import com.yupi.template.agent.run.AgentRunStatus;
import com.yupi.template.mapper.AgentRunMapper;
import com.yupi.template.model.entity.AgentRunRecord;
import com.yupi.template.service.AgentRunService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class AgentRunServiceImpl extends ServiceImpl<AgentRunMapper, AgentRunRecord> implements AgentRunService {

    @Override
    public AgentRunRecord createRootRun(String taskId) {
        AgentRunRecord record = AgentRunRecord.builder()
                .runId(taskId)
                .rootRunId(taskId)
                .taskId(taskId)
                .status(AgentRunStatus.PENDING.name())
                .createTime(LocalDateTime.now())
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
        return new AgentRun(
                record.getRunId(), record.getRootRunId(), record.getParentRunId(),
                AgentRunStatus.valueOf(record.getStatus()),
                createdAt.atZone(ZoneId.systemDefault()).toInstant(),
                updatedAt.atZone(ZoneId.systemDefault()).toInstant()
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
}
