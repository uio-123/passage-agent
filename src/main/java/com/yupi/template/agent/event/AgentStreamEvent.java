package com.yupi.template.agent.event;

import com.yupi.template.model.enums.SseMessageTypeEnum;

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
