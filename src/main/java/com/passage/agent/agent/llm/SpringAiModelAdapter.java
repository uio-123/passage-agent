package com.passage.agent.agent.llm;

import com.passage.agent.utils.GsonUtils;
import com.passage.agent.agent.metrics.ModelCallMeasurement;
import com.passage.agent.agent.metrics.WorkflowMetricsCollector;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.lang.reflect.Type;
import java.lang.reflect.Method;

/** Adapts Spring AI to the narrow model port used by this application. */
@Component
public class SpringAiModelAdapter implements AiModelPort {

    private final ChatModel chatModel;
    private final WorkflowMetricsCollector metricsCollector;

    /** Compatibility constructor for adapter HTTP tests; production wiring records scoped measurements. */
    public SpringAiModelAdapter(ChatModel chatModel) {
        this(chatModel, new WorkflowMetricsCollector());
    }

    @Autowired
    public SpringAiModelAdapter(ChatModel chatModel, WorkflowMetricsCollector metricsCollector) {
        this.chatModel = chatModel;
        this.metricsCollector = metricsCollector;
    }

    @Override
    public String complete(String prompt) {
        long started = System.nanoTime();
        var response = chatModel.call(new Prompt(new UserMessage(prompt)));
        metricsCollector.recordModelMeasurement(measure(response, "complete", null, elapsed(started), null));
        return requireText(response.getResult().getOutput().getText());
    }

    @Override
    public Flux<String> stream(String prompt) {
        long started = System.nanoTime();
        java.util.concurrent.atomic.AtomicLong firstToken = new java.util.concurrent.atomic.AtomicLong(-1);
        return chatModel.stream(new Prompt(new UserMessage(prompt)))
                .map(response -> {
                    String text = response.getResult().getOutput().getText();
                    return text == null ? "" : text;
                })
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .doOnNext(chunk -> firstToken.compareAndSet(-1, elapsed(started)))
                .doOnComplete(() -> metricsCollector.recordModelMeasurement(new ModelCallMeasurement(
                        "stream", null, null, null, null, firstToken.get() < 0 ? null : firstToken.get(), elapsed(started), 0, null)));
    }

    @Override
    public <T> T completeStructured(String prompt, Type responseType) {
        T result = GsonUtils.fromJson(StructuredJsonExtractor.extract(complete(prompt)), responseType);
        if (result == null) {
            throw new IllegalStateException("模型未返回可解析的结构化结果");
        }
        return result;
    }

    private String requireText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("模型未返回文本内容");
        }
        return text;
    }

    private ModelCallMeasurement measure(Object response, String callType, Long firstTokenMs, long durationMs, String errorCode) {
        Object metadata = invoke(response, "getMetadata");
        Object usage = metadata == null ? null : invoke(metadata, "getUsage");
        return new ModelCallMeasurement(callType, asText(invoke(metadata, "getModel")), asLong(invoke(usage, "getPromptTokens")),
                asLong(invoke(usage, "getCompletionTokens")), asLong(invoke(usage, "getTotalTokens")), firstTokenMs, durationMs, 0, errorCode);
    }
    private Object invoke(Object target, String method) { if (target == null) return null; try { Method m = target.getClass().getMethod(method); return m.invoke(target); } catch (ReflectiveOperationException ignored) { return null; } }
    private Long asLong(Object value) { return value instanceof Number number ? number.longValue() : null; }
    private String asText(Object value) { return value == null ? null : value.toString(); }
    private long elapsed(long started) { return java.time.Duration.ofNanos(System.nanoTime() - started).toMillis(); }
}
