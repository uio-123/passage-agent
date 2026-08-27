package com.yupi.template.agent.event;

/**
 * Temporary compatibility boundary for the existing string-based SSE pipeline.
 * The browser contract remains unchanged while internal producers use DTOs.
 */
public final class AgentStreamEventMapper {

    private AgentStreamEventMapper() {
    }

    public static String toLegacyMessage(AgentStreamEvent event) {
        return event.type().getStreamingPrefix() + event.delta();
    }
}
