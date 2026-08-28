package com.passage.agent.agent;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static org.assertj.core.api.Assertions.assertThat;

/** Isolated proof that the candidate graph version can checkpoint and resume by thread id. */
class CheckpointCompatibilityTest {

    @Test
    void resumesFromCheckpointWithoutReplayingTheInterruptedNode() throws Exception {
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        MemorySaver saver = new MemorySaver();
        StateGraph graph = new StateGraph(() -> new HashMap<>(Map.of(
                "input", new ReplaceStrategy(),
                "first", new ReplaceStrategy(),
                "second", new ReplaceStrategy()
        )))
                .addNode("first", node_async(state -> {
                    firstCalls.incrementAndGet();
                    return Map.of("first", "completed");
                }))
                .addNode("second", node_async(state -> {
                    secondCalls.incrementAndGet();
                    return Map.of("second", state.value("first").orElseThrow());
                }))
                .addEdge(START, "first")
                .addEdge("first", "second")
                .addEdge("second", END);

        CompiledGraph compiled = graph.compile(CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(saver).build())
                .interruptAfter("first")
                .build());
        RunnableConfig initialRun = RunnableConfig.builder().threadId("checkpoint-contract-1").build();

        compiled.invoke(Map.of("input", "value"), initialRun);

        assertThat(firstCalls).hasValue(1);
        assertThat(secondCalls).hasValue(0);
        StateSnapshot paused = compiled.getState(initialRun);
        assertThat(paused.next()).isEqualTo("second");

        RunnableConfig resumedRun = RunnableConfig.builder(paused.config()).resume().build();
        Map<String, Object> resumed = compiled.invoke(Map.of(), resumedRun).orElseThrow().data();

        assertThat(firstCalls).hasValue(1);
        assertThat(secondCalls).hasValue(1);
        assertThat(resumed).containsEntry("second", "completed");
    }
}
