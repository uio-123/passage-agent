package com.passage.agent.service;

import com.passage.agent.agent.event.AgentEvent;
import com.passage.agent.agent.event.AgentEventInput;
import java.util.List;

public interface AgentEventService {
    AgentEvent append(String runId, AgentEventInput input);
    List<AgentEvent> findAfter(String runId, long afterSequence, int limit);
}
