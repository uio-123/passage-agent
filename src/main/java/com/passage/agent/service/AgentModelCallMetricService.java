package com.passage.agent.service;
import com.passage.agent.agent.metrics.ModelCallMeasurement;
public interface AgentModelCallMetricService { void record(String runId,String stage,int attempt,int callIndex,ModelCallMeasurement measurement); }
