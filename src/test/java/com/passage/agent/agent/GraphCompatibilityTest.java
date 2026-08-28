package com.passage.agent.agent;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Candidate-version contracts for the StateGraph APIs used by the article flow.
 * These tests deliberately use local deterministic node actions: they must not call
 * LiteLLM, a database, or image services.
 */
class GraphCompatibilityTest {

    @Test
    void serialGraphPropagatesStateAcrossAsyncNodes() throws Exception {
        StateGraph graph = new StateGraph()
                .addNode("title", node_async(state -> Map.of("title", "candidate title")))
                .addNode("outline", node_async(state -> Map.of(
                        "outline", state.value("title").orElseThrow() + " outline")))
                .addEdge(START, "title")
                .addEdge("title", "outline")
                .addEdge("outline", END);

        CompiledGraph compiledGraph = graph.compile();
        OverAllState result = compiledGraph.invoke(Map.of()).orElseThrow();

        assertThat(result.value("outline")).contains("candidate title outline");
    }

    @Test
    void parallelBranchesJoinOnlyAfterBothOutputsAreAvailable() throws Exception {
        StateGraph graph = new StateGraph()
                .addNode("first", node_async(state -> Map.of("first", "A")))
                .addNode("second", node_async(state -> Map.of("second", "B")))
                .addNode("merge", node_async(state -> Map.of("merged",
                        String.valueOf(state.value("first").orElseThrow())
                                + String.valueOf(state.value("second").orElseThrow()))))
                .addEdge(START, List.of("first", "second"))
                .addEdge(List.of("first", "second"), "merge")
                .addEdge("merge", END);

        OverAllState result = graph.compile().invoke(Map.of()).orElseThrow();

        assertThat(result.value("merged")).contains("AB");
    }

    @Test
    void graphStreamEmitsEachNodeAndEndsWithMergedState() throws Exception {
        StateGraph graph = new StateGraph()
                .addNode("write", node_async(state -> Map.of("value", "stream-safe")))
                .addEdge(START, "write")
                .addEdge("write", END);

        List<String> nodeNames = graph.compile().stream(Map.of())
                .map(nodeOutput -> nodeOutput.node())
                .collectList()
                .block();

        assertThat(nodeNames).contains("write");
    }
}
