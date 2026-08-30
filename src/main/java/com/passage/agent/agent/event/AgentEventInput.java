package com.passage.agent.agent.event;

import java.util.Map;

/** Event supplied by business code before it is sanitised and assigned a durable sequence. */
public record AgentEventInput(AgentEventType eventType, String nodeId, String agentName,
                              Integer attempt, Map<String, String> payload) {
}
