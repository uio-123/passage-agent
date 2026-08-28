package com.passage.agent.agent.llm;

import com.passage.agent.agent.metrics.WorkflowMetricsCollector;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.lang.reflect.Type;

/** Counts project model-port invocations while a workflow stage is active. */
@Component
@Primary
public class MetricsCollectingAiModelPort implements AiModelPort {

    private final AiModelPort delegate;
    private final WorkflowMetricsCollector metricsCollector;

    public MetricsCollectingAiModelPort(@Qualifier("springAiModelAdapter") AiModelPort delegate,
                                        WorkflowMetricsCollector metricsCollector) {
        this.delegate = delegate;
        this.metricsCollector = metricsCollector;
    }

    @Override
    public String complete(String prompt) {
        metricsCollector.recordModelCall();
        return delegate.complete(prompt);
    }

    @Override
    public Flux<String> stream(String prompt) {
        metricsCollector.recordModelCall();
        return delegate.stream(prompt);
    }

    @Override
    public <T> T completeStructured(String prompt, Type responseType) {
        metricsCollector.recordModelCall();
        return delegate.completeStructured(prompt, responseType);
    }
}
