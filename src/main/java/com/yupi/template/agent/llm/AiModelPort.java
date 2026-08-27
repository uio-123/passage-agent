package com.yupi.template.agent.llm;

import reactor.core.publisher.Flux;

import java.lang.reflect.Type;

/**
 * Project-owned boundary for model calls. Agent code must not expose Spring AI
 * response types to graph state, SSE events, or callers.
 */
public interface AiModelPort {

    String complete(String prompt);

    Flux<String> stream(String prompt);

    <T> T completeStructured(String prompt, Type responseType);
}
