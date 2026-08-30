package com.passage.agent.model.vo;
import java.util.List;
/** Sanitised administrator-only operational aggregate. */
public record AgentGovernanceMetrics(long sampleCount,long successCount,long failureCount,double successRate,double avgDurationMs,
                                     long p50DurationMs,long p95DurationMs,List<AgentMetric> agents,UnavailableMetric token,UnavailableMetric cost,UnavailableMetric firstToken) {
 public record AgentMetric(String agentName,long sampleCount,double failureRate,double avgDurationMs,boolean slow,boolean highFailure){}
 public record UnavailableMetric(boolean available,String reason){}
}
