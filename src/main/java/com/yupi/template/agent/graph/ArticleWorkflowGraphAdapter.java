package com.yupi.template.agent.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.yupi.template.agent.ArticleAgentOrchestrator;
import com.yupi.template.model.dto.article.ArticleState;
import com.yupi.template.utils.GsonUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Consumer;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/** One routed StateGraph definition; approval is enforced before graph entry. */
@Service
@Primary
public class ArticleWorkflowGraphAdapter implements ArticleWorkflowExecutor {
    private static final String LEGACY_STATE = "legacy_article_state";
    private static final String ENTRY = "workflow_entry";
    private final ArticleAgentOrchestrator legacy;
    private final ThreadLocal<Consumer<String>> handlers = new ThreadLocal<>();
    private volatile CompiledGraph graph;

    public ArticleWorkflowGraphAdapter(ArticleAgentOrchestrator legacy) {
        this.legacy = legacy;
    }

    @PostConstruct
    void initialize() { graph(); }

    @Override public void executeTitles(ArticleState state, Consumer<String> handler) { invoke("titles", state, handler); }
    @Override public void executeOutline(ArticleState state, Consumer<String> handler) { invoke("outline", state, handler); }
    @Override public void executeContent(ArticleState state, Consumer<String> handler) { invoke("content", state, handler); }

    private void invoke(String entry, ArticleState state, Consumer<String> handler) {
        handlers.set(handler);
        try {
            OverAllState result = graph().invoke(Map.of(ENTRY, entry, LEGACY_STATE, state))
                    .orElseThrow(() -> new IllegalStateException("Article workflow returned no state"));
            ArticleState output = result.value(LEGACY_STATE).filter(ArticleState.class::isInstance)
                    .map(ArticleState.class::cast).orElseThrow(() -> new IllegalStateException("Article workflow returned no article state"));
            copyOutput(state, output);
        } finally { handlers.remove(); }
    }

    private CompiledGraph graph() {
        if (graph == null) synchronized (this) {
            if (graph == null) try {
                graph = new StateGraph()
                        .addNode("route", node_async(state -> Map.of()))
                        .addNode("titles", node_async(state -> run(state, legacy::executePhase1_GenerateTitles)))
                        .addNode("outline", node_async(state -> run(state, legacy::executePhase2_GenerateOutline)))
                        .addNode("content", node_async(state -> run(state, legacy::executePhase3_GenerateContent)))
                        .addEdge(START, "route")
                        .addConditionalEdges("route", edge_async(state ->
                                state.value(ENTRY).map(Object::toString).orElseThrow()),
                                Map.of("titles", "titles", "outline", "outline", "content", "content"))
                        .addEdge("titles", END).addEdge("outline", END).addEdge("content", END).compile();
            } catch (GraphStateException exception) { throw new IllegalStateException("Unable to compile article workflow graph", exception); }
        }
        return graph;
    }

    private Map<String, Object> run(OverAllState graphState, StageExecutor executor) {
        ArticleState state = graphState.value(LEGACY_STATE).filter(ArticleState.class::isInstance)
                .map(ArticleState.class::cast).orElseThrow(() -> new IllegalStateException("Missing article state"));
        executor.execute(state, handlers.get());
        return Map.of(LEGACY_STATE, state);
    }

    private void copyOutput(ArticleState target, ArticleState source) {
        ArticleState copy = GsonUtils.fromJson(GsonUtils.toJson(source), ArticleState.class);
        target.setTitleOptions(copy.getTitleOptions());
        target.setTitle(copy.getTitle());
        target.setOutline(copy.getOutline());
        target.setContent(copy.getContent());
        target.setImageRequirements(copy.getImageRequirements());
        target.setImages(copy.getImages());
        target.setCoverImage(copy.getCoverImage());
        target.setFullContent(copy.getFullContent());
    }

    @FunctionalInterface private interface StageExecutor { void execute(ArticleState state, Consumer<String> handler); }
}
