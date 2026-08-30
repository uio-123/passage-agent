package com.passage.agent.service;
import com.passage.agent.agent.policy.ToolCallAuditEvent;
import com.passage.agent.model.entity.ToolCallAuditRecord;
import java.util.List;
public interface ToolCallAuditService {
    void record(ToolCallAuditEvent event);
    List<ToolCallAuditRecord> listByRunId(String runId);
}
