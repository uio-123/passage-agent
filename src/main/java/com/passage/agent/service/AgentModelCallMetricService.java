package com.passage.agent.service;

import com.passage.agent.agent.metrics.ModelCallMeasurement;
import com.passage.agent.model.entity.AgentModelCallMetricRecord;

import java.util.List;

public interface AgentModelCallMetricService {
    void record(String runId, String stage, int attempt, int callIndex, ModelCallMeasurement measurement);

    List<AgentModelCallMetricRecord> listByRunId(String runId);
}
