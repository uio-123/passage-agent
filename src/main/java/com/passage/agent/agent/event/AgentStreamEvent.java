package com.passage.agent.agent.event;

import com.passage.agent.model.enums.SseMessageTypeEnum;

import java.time.Instant;

/** Internal, framework-neutral representation of one streamed model delta. */
public record AgentStreamEvent(
        String taskId,
        String nodeId,
        SseMessageTypeEnum type,
        long sequence,
        String delta,
        Instant occurredAt
) {
}
