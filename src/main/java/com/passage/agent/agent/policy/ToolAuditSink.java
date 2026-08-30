package com.passage.agent.agent.policy;

@FunctionalInterface
public interface ToolAuditSink {
    void record(ToolCallAuditEvent event);
}
