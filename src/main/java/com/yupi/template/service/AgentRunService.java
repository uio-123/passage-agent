package com.yupi.template.service;

import com.mybatisflex.core.service.IService;
import com.yupi.template.agent.run.AgentRun;
import com.yupi.template.model.entity.AgentRunRecord;

/** Persistence operations for article root and child runs. */
public interface AgentRunService extends IService<AgentRunRecord> {

    AgentRunRecord createRootRun(String taskId);

    AgentRunRecord createChildRun(String runId, String parentRunId);

    AgentRunRecord getByRunId(String runId);

    AgentRun getDomain(String runId);

    void sync(AgentRun run, String currentNode);

    void markFailed(String runId, String errorMessage);

    boolean cancel(String runId);
}
