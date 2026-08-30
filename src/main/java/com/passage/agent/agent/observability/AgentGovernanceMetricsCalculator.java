package com.passage.agent.agent.observability;

import com.passage.agent.model.entity.AgentLog;
import com.passage.agent.model.vo.AgentGovernanceMetrics;
import java.util.*;
import java.util.stream.Collectors;

/** Pure, sanitised aggregation: consumes status and duration only. */
public final class AgentGovernanceMetricsCalculator {
    private AgentGovernanceMetricsCalculator() { }
    public static AgentGovernanceMetrics calculate(List<AgentLog> source) {
        List<AgentLog> logs = source == null ? List.of() : source.stream().filter(log -> log.getDurationMs() != null && log.getDurationMs() >= 0).toList();
        long success = logs.stream().filter(log -> "SUCCESS".equals(log.getStatus())).count();
        long failed = logs.stream().filter(log -> "FAILED".equals(log.getStatus())).count();
        List<Integer> durations = logs.stream().map(AgentLog::getDurationMs).sorted().toList();
        double avg = durations.stream().mapToInt(Integer::intValue).average().orElse(0);
        var agents = logs.stream().collect(Collectors.groupingBy(log -> Optional.ofNullable(log.getAgentName()).orElse("unknown"))).entrySet().stream().map(entry -> {
            var values = entry.getValue(); double agentAvg = values.stream().mapToInt(AgentLog::getDurationMs).average().orElse(0);
            double failureRate = values.stream().filter(log -> "FAILED".equals(log.getStatus())).count() * 100.0 / values.size();
            return new AgentGovernanceMetrics.AgentMetric(entry.getKey(), values.size(), failureRate, agentAvg, agentAvg >= avg && values.size() >= 2, failureRate >= 10.0);
        }).sorted(Comparator.comparingDouble(AgentGovernanceMetrics.AgentMetric::avgDurationMs).reversed()).toList();
        long count = logs.size(); var unavailable = new AgentGovernanceMetrics.UnavailableMetric(false, "当前未持久化可信采集数据");
        return new AgentGovernanceMetrics(count, success, failed, count == 0 ? 0 : success * 100.0 / count, avg, percentile(durations, .50), percentile(durations, .95), agents, unavailable, unavailable, unavailable);
    }
    private static long percentile(List<Integer> values, double p) { return values.isEmpty() ? 0 : values.get((int) Math.ceil(p * values.size()) - 1); }
}
