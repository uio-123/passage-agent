package com.passage.agent.manager;

import com.passage.agent.agent.event.AgentEvent;
import com.passage.agent.agent.event.AgentRunSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/** Dedicated multi-subscriber stream; independent from the legacy article SSE manager. */
@Component
public class AgentEventSseManager {
    private final Map<String, CopyOnWriteArraySet<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    public SseEmitter subscribe(String runId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(runId, ignored -> new CopyOnWriteArraySet<>()).add(emitter);
        Runnable remove = () -> remove(runId, emitter);
        emitter.onCompletion(remove); emitter.onTimeout(remove); emitter.onError(ignored -> remove.run());
        return emitter;
    }
    public void publish(AgentEvent event) {
        for (SseEmitter emitter : subscribers.getOrDefault(event.runId(), new CopyOnWriteArraySet<>())) {
            try { emitter.send(SseEmitter.event().id(Long.toString(event.sequence())).name("agent-event").data(event)); }
            catch (IOException exception) { remove(event.runId(), emitter); }
        }
    }
    public void sendSnapshot(SseEmitter emitter, AgentRunSnapshot snapshot) throws IOException { emitter.send(SseEmitter.event().name("snapshot").data(snapshot)); }
    private void remove(String runId, SseEmitter emitter) { var set = subscribers.get(runId); if (set != null) { set.remove(emitter); if (set.isEmpty()) subscribers.remove(runId, set); } }
}
