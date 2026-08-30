package com.passage.agent.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.passage.agent.agent.policy.ToolCallAuditEvent;
import com.passage.agent.mapper.ToolCallAuditMapper;
import com.passage.agent.model.entity.ToolCallAuditRecord;
import com.passage.agent.service.ToolCallAuditService;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ToolCallAuditServiceImpl extends ServiceImpl<ToolCallAuditMapper, ToolCallAuditRecord> implements ToolCallAuditService {
    @Override public void record(ToolCallAuditEvent event) {
        ToolCallAuditRecord record = new ToolCallAuditRecord();
        record.setRunId(event.runId()); record.setToolId(event.toolId().name()); record.setTarget(event.target());
        record.setSuccessful(event.successful()); record.setErrorCode(event.error() == null ? null : event.error().name());
        record.setElapsedMillis(event.elapsed().toMillis()); record.setRetryCount(event.retries()); record.setResponseBytes(event.responseBytes());
        record.setCreateTime(LocalDateTime.now()); save(record);
    }
    @Override public List<ToolCallAuditRecord> listByRunId(String runId) {
        return list(QueryWrapper.create().eq("runId", runId).orderBy("id", true));
    }
}
