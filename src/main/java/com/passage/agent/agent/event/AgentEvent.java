package com.passage.agent.agent.event;

import java.time.Instant;
import java.util.Map;

/** Read-only, sanitised event representation delivered to the API and SSE clients. */
public record AgentEvent(String runId, long sequence, AgentEventType eventType, String nodeId,
                         String agentName, Integer attempt, Map<String, String> payload, Instant occurredAt) {
}
