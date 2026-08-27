package com.yupi.template.agent.event;

import com.yupi.template.agent.context.StreamHandlerContext;
import com.yupi.template.model.enums.SseMessageTypeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentStreamEventContractTest {

    @AfterEach
    void clearContext() {
        StreamHandlerContext.clear();
    }

    @Test
    void emitsFrameworkNeutralEventsAndKeepsLegacySseFormatAtBoundary() {
        List<AgentStreamEvent> events = new ArrayList<>();
        StreamHandlerContext.set("task-1", events::add);

        StreamHandlerContext.send("outline_generator", SseMessageTypeEnum.AGENT2_STREAMING, "first");
        StreamHandlerContext.send("outline_generator", SseMessageTypeEnum.AGENT2_STREAMING, "second");

        assertThat(events).extracting(AgentStreamEvent::taskId).containsOnly("task-1");
        assertThat(events).extracting(AgentStreamEvent::nodeId).containsOnly("outline_generator");
        assertThat(events).extracting(AgentStreamEvent::sequence).containsExactly(1L, 2L);
        assertThat(AgentStreamEventMapper.toLegacyMessage(events.getFirst()))
                .isEqualTo("AGENT2_STREAMING:first");
    }
}
