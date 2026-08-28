package com.yupi.template.agent.context;

import com.yupi.template.agent.event.AgentStreamEvent;
import com.yupi.template.model.enums.SseMessageTypeEnum;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 流式输出处理器上下文
 * 使用 ThreadLocal 保存 streamHandler，避免将其放入 StateGraph 状态中（无法序列化）
 *
 * @author AI Passage Creator
 */
public class StreamHandlerContext {

    private static final ThreadLocal<Consumer<AgentStreamEvent>> STREAM_HANDLER = new ThreadLocal<>();

    private static final ThreadLocal<String> TASK_ID = new ThreadLocal<>();

    private static final ThreadLocal<AtomicLong> SEQUENCE = new ThreadLocal<>();

    /**
     * 设置流式输出处理器
     *
     * @param handler 处理器
     */
    public static void set(String taskId, Consumer<AgentStreamEvent> handler) {
        TASK_ID.set(taskId);
        STREAM_HANDLER.set(handler);
        SEQUENCE.set(new AtomicLong());
    }

    /**
     * 获取流式输出处理器
     *
     * @return 处理器，可能为 null
     */
    public static Consumer<AgentStreamEvent> get() {
        return STREAM_HANDLER.get();
    }

    /**
     * Captures the current event publisher so asynchronous work can emit events
     * after it leaves the caller thread. The sequence counter remains shared.
     */
    public static StreamEventPublisher capture() {
        return new StreamEventPublisher(TASK_ID.get(), STREAM_HANDLER.get(), SEQUENCE.get());
    }

    /**
     * 清理上下文
     * 务必在使用完毕后调用，避免内存泄漏
     */
    public static void clear() {
        STREAM_HANDLER.remove();
        TASK_ID.remove();
        SEQUENCE.remove();
    }

    /**
     * 发送消息到流式输出
     * 如果 handler 不存在则忽略
     *
     * @param message 消息内容
     */
    public static void send(String nodeId, SseMessageTypeEnum type, String delta) {
        capture().publish(nodeId, type, delta);
    }

    public record StreamEventPublisher(
            String taskId,
            Consumer<AgentStreamEvent> handler,
            AtomicLong sequence
    ) {
        public void publish(String nodeId, SseMessageTypeEnum type, String delta) {
            if (handler != null && sequence != null && delta != null) {
                // A captured publisher is shared by parallel graph branches. Keep
                // increment and delivery atomic so non-thread-safe SSE consumers
                // cannot lose an event or observe sequence numbers out of order.
                synchronized (handler) {
                    handler.accept(new AgentStreamEvent(
                            taskId, nodeId, type, sequence.incrementAndGet(), delta, Instant.now()));
                }
            }
        }
    }
}
