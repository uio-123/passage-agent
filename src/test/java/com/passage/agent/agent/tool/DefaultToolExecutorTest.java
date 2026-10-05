package com.passage.agent.agent.tool;

import com.passage.agent.agent.policy.ToolPolicy;
import com.passage.agent.agent.policy.ToolPolicyError;
import com.passage.agent.agent.policy.ToolPolicyException;
import com.passage.agent.agent.policy.ToolPolicyGateway;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultToolExecutorTest {

    @Test
    void delegatesAnAuthorizedCallToTheExistingPolicyGateway() {
        AtomicInteger calls = new AtomicInteger();
        ToolExecutor executor = executor(request -> {
            calls.incrementAndGet();
            return new ToolCallResult(ToolId.WEB_READER, List.of(), Duration.ZERO);
        });
        ToolContext context = new ToolContext("run-h3", "research",
                Set.of(ToolId.WEB_READER), 1);

        ToolCallResult result = executor.execute(context, ToolId.WEB_READER, "https://example.com");

        assertThat(result.toolId()).isEqualTo(ToolId.WEB_READER);
        assertThat(calls).hasValue(1);
    }

    @Test
    void rejectsAToolOutsideTheAgentContextBeforeExecution() {
        AtomicInteger calls = new AtomicInteger();
        ToolExecutor executor = executor(request -> {
            calls.incrementAndGet();
            return new ToolCallResult(ToolId.WEB_READER, List.of(), Duration.ZERO);
        });
        ToolContext context = new ToolContext("run-h3", "research", Set.of(ToolId.SEARCH), 1);

        assertThatThrownBy(() -> executor.execute(context, ToolId.WEB_READER, "https://example.com"))
                .isInstanceOf(ToolPolicyException.class)
                .extracting(exception -> ((ToolPolicyException) exception).error())
                .isEqualTo(ToolPolicyError.UNAUTHORIZED_TOOL);
        assertThat(calls).hasValue(0);
    }

    private static ToolExecutor executor(java.util.function.Function<ToolCallRequest, ToolCallResult> action) {
        ToolAdapter adapter = new ToolAdapter() {
            @Override
            public ToolId id() {
                return ToolId.WEB_READER;
            }

            @Override
            public ToolCallResult execute(ToolCallRequest request, ToolPolicy policy) {
                return action.apply(request);
            }
        };
        ToolPolicyGateway gateway = new ToolPolicyGateway(
                new ToolRegistry(List.of(adapter)), ToolPolicy.strictDefaults(), event -> { });
        return new DefaultToolExecutor(gateway);
    }
}
