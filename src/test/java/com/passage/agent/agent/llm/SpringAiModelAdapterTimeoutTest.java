package com.passage.agent.agent.llm;

import com.passage.agent.agent.metrics.WorkflowMetricsCollector;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiModelAdapterTimeoutTest {

    @Test
    void cancelsBlockingCallAfterTheConfiguredDeadline() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Thread.sleep(500);
            return null;
        });
        SpringAiModelAdapter adapter = new SpringAiModelAdapter(
                chatModel, new WorkflowMetricsCollector(), Duration.ofMillis(30));
        try {
            assertThatThrownBy(() -> adapter.complete("hello"))
                    .isInstanceOf(ModelCallTimeoutException.class)
                    .hasMessageContaining("complete");
        } finally {
            adapter.shutdownExecutor();
        }
    }

    @Test
    void cancelsAStreamThatNeverCompletes() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.never());
        SpringAiModelAdapter adapter = new SpringAiModelAdapter(
                chatModel, new WorkflowMetricsCollector(), Duration.ofMillis(30));
        try {
            assertThatThrownBy(() -> adapter.stream("hello").blockLast())
                    .isInstanceOf(ModelCallTimeoutException.class)
                    .hasMessageContaining("stream");
        } finally {
            adapter.shutdownExecutor();
        }
    }
}
