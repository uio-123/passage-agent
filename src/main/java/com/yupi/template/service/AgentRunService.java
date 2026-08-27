package com.yupi.template.service;

import com.mybatisflex.core.service.IService;
import com.yupi.template.agent.run.AgentRun;
import com.yupi.template.model.entity.AgentRunRecord;

/** Persistence operations for article root runs; child-run persistence follows in P1. */
public interface AgentRunService extends IService<AgentRunRecord> {

    AgentRunRecord createRootRun(String taskId);

    AgentRunRecord getByRunId(String runId);

    AgentRun getDomain(String runId);

    void sync(AgentRun run, String currentNode);

    void markFailed(String runId, String errorMessage);
}
