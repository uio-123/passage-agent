package com.passage.agent.agent.event;

/** Safe current-state projection sent before replayed event increments. */
public record AgentRunSnapshot(String runId, String rootRunId, String parentRunId, String status,
                               String currentNode, long stateVersion, String checkpointId) {
}
