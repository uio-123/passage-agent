package com.passage.agent.agent.llm;

import com.passage.agent.utils.GsonUtils;
import com.passage.agent.agent.metrics.ModelCallMeasurement;
import com.passage.agent.agent.metrics.WorkflowMetricsCollector;
import jakarta.annotation.PreDestroy;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.lang.reflect.Type;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** Adapts Spring AI to the narrow model port used by this application. */
@Component
public class SpringAiModelAdapter implements AiModelPort {

    private final ChatModel chatModel;
    private final WorkflowMetricsCollector metricsCollector;
    private final Duration modelTimeout;
    private final ExecutorService blockingCalls = Executors.newVirtualThreadPerTaskExecutor();

    /** Compatibility constructor for adapter HTTP tests; production wiring records scoped measurements. */
    public SpringAiModelAdapter(ChatModel chatModel) {
        this(chatModel, new WorkflowMetricsCollector());
    }

    @Autowired
    public SpringAiModelAdapter(ChatModel chatModel, WorkflowMetricsCollector metricsCollector,
                                @Value("${article.agent.model-timeout:120s}") Duration modelTimeout) {
        this.chatModel = chatModel;
        this.metricsCollector = metricsCollector;
        this.modelTimeout = modelTimeout;
    }

    public SpringAiModelAdapter(ChatModel chatModel, WorkflowMetricsCollector metricsCollector) {
        this(chatModel, metricsCollector, Duration.ofSeconds(120));
    }

    @Override
    public String complete(String prompt) {
        long started = System.nanoTime();
        Future<ChatResponse> future = blockingCalls.submit(() -> chatModel.call(new Prompt(new UserMessage(prompt))));
        try {
            ChatResponse response = future.get(modelTimeout.toMillis(), TimeUnit.MILLISECONDS);
            metricsCollector.recordModelMeasurement(measure(response, "complete", null, elapsed(started), null));
            return requireText(response.getResult().getOutput().getText());
        } catch (TimeoutException exception) {
            future.cancel(true);
            metricsCollector.recordModelMeasurement(new ModelCallMeasurement(
                    "complete", null, null, null, null, null, elapsed(started), 0, "MODEL_TIMEOUT"));
            throw new ModelCallTimeoutException("complete", modelTimeout);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ModelCallTimeoutException("complete", modelTimeout);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Model complete call failed", cause);
        }
    }

    @Override
    public Flux<String> stream(String prompt) {
        long started = System.nanoTime();
        java.util.concurrent.atomic.AtomicLong firstToken = new java.util.concurrent.atomic.AtomicLong(-1);
        AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();
        return chatModel.stream(new Prompt(new UserMessage(prompt)))
                .timeout(modelTimeout)
                .onErrorMap(TimeoutException.class, exception -> new ModelCallTimeoutException("stream", modelTimeout))
                .map(response -> {
                    lastResponse.set(response);
                    String text = response.getResult().getOutput().getText();
                    return text == null ? "" : text;
                })
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .doOnNext(chunk -> firstToken.compareAndSet(-1, elapsed(started)))
                .doOnComplete(() -> metricsCollector.recordModelMeasurement(
                        measure(lastResponse.get(), "stream",
                                firstToken.get() < 0 ? null : firstToken.get(), elapsed(started), null)));
    }

    @PreDestroy
    void shutdownExecutor() {
        blockingCalls.shutdownNow();
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
        Long inputTokens = asLong(invoke(usage, "getPromptTokens"));
        Long outputTokens = asLong(invoke(usage, "getCompletionTokens"));
        Long totalTokens = asLong(invoke(usage, "getTotalTokens"));
        if (isZero(inputTokens) && isZero(outputTokens) && isZero(totalTokens)) {
            inputTokens = null;
            outputTokens = null;
            totalTokens = null;
        }
        return new ModelCallMeasurement(callType, asText(invoke(metadata, "getModel")), inputTokens,
                outputTokens, totalTokens, firstTokenMs, durationMs, 0, errorCode);
    }
    private boolean isZero(Long value) { return value == null || value == 0L; }
    private Object invoke(Object target, String method) { if (target == null) return null; try { Method m = target.getClass().getMethod(method); return m.invoke(target); } catch (ReflectiveOperationException ignored) { return null; } }
    private Long asLong(Object value) { return value instanceof Number number ? number.longValue() : null; }
    private String asText(Object value) { return value == null ? null : value.toString(); }
    private long elapsed(long started) { return java.time.Duration.ofNanos(System.nanoTime() - started).toMillis(); }
}
