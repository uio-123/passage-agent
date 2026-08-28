package com.yupi.template.service;

import com.mybatisflex.core.service.IService;
import com.yupi.template.agent.checkpoint.NodeExecutionOutcome;
import com.yupi.template.model.entity.AgentNodeExecutionRecord;

import java.util.function.Supplier;

/** Durable idempotency boundary for a workflow node and its externally visible result. */
public interface AgentNodeExecutionService extends IService<AgentNodeExecutionRecord> {

    NodeExecutionOutcome executeOnce(String runId, String nodeId, long stateVersion, Supplier<String> action);
}
