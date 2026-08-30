package com.passage.agent.agent.event;

/** Best-effort publication boundary; callers must never let observability failure alter workflow outcome. */
public interface AgentEventPublisher {
    void publish(String runId, AgentEventInput event);
}
