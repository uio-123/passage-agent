package com.passage.agent.service;

import com.mybatisflex.core.service.IService;
import com.passage.agent.agent.checkpoint.NodeExecutionOutcome;
import com.passage.agent.model.entity.AgentNodeExecutionRecord;

import java.util.function.Supplier;

/** Durable idempotency boundary for a workflow node and its externally visible result. */
public interface AgentNodeExecutionService extends IService<AgentNodeExecutionRecord> {

    NodeExecutionOutcome executeOnce(String runId, String nodeId, long stateVersion, Supplier<String> action);
}
