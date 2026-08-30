package com.passage.agent.agent.event;

import com.passage.agent.manager.AgentEventSseManager;
import com.passage.agent.service.AgentEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component @Slf4j
public class PersistentAgentEventPublisher implements AgentEventPublisher {
    private final AgentEventService events;
    private final AgentEventSseManager sse;
    private final boolean enabled;
    public PersistentAgentEventPublisher(AgentEventService events, AgentEventSseManager sse,
                                         @Value("${article.agent.observability.events-enabled:false}") boolean enabled) {
        this.events = events; this.sse = sse; this.enabled = enabled;
    }
    @Override public void publish(String runId, AgentEventInput event) {
        if (!enabled) return;
        try { sse.publish(events.append(runId, event)); }
        catch (RuntimeException exception) { log.warn("Agent event publication failed; workflow result is unaffected, runId={}, type={}", runId, event.eventType(), exception); }
    }
}
