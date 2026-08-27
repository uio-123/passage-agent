package com.yupi.template.agent.workflow;

import com.yupi.template.agent.ArticleAgentOrchestrator;
import com.yupi.template.agent.graph.ArticleWorkflowExecutor;
import com.yupi.template.agent.api.WorkflowRunner;
import com.yupi.template.agent.api.WorkflowError;
import com.yupi.template.agent.api.WorkflowErrorCode;
import com.yupi.template.agent.api.WorkflowExecutionException;
import com.yupi.template.agent.api.WorkflowExecutionResult;
import com.yupi.template.agent.api.WorkflowStage;
import com.yupi.template.agent.metrics.WorkflowMetricsCollector;
import com.yupi.template.agent.run.AgentRun;
import com.yupi.template.agent.run.AgentRunStatus;
import com.yupi.template.agent.state.WorkflowState;
import com.yupi.template.agent.state.WorkflowStateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Transitional runner which exposes typed state while delegating execution to
 * the current graph-backed article orchestrator. It keeps framework state out
 * of service callers until the unified graph replaces the legacy phases.
 */
@Service
public class ArticleWorkflowRunner implements WorkflowRunner {

    private final ArticleWorkflowExecutor graphExecutor;
    private final WorkflowMetricsCollector metricsCollector;

    public ArticleWorkflowRunner(ArticleAgentOrchestrator orchestrator) {
        this(orchestrator, new WorkflowMetricsCollector());
    }

    public ArticleWorkflowRunner(ArticleAgentOrchestrator orchestrator, WorkflowMetricsCollector metricsCollector) {
        this((ArticleWorkflowExecutor) orchestrator, metricsCollector);
    }

    @Autowired
    public ArticleWorkflowRunner(ArticleWorkflowExecutor graphExecutor, WorkflowMetricsCollector metricsCollector) {
        this.graphExecutor = graphExecutor;
        this.metricsCollector = metricsCollector;
    }

    @Override
    public WorkflowExecutionResult generateTitles(WorkflowState state, Consumer<String> streamHandler) {
        return metricsCollector.measure(state.run().runId(), WorkflowStage.TITLES_GENERATED,
                () -> generateTitlesInternal(state, streamHandler));
    }

    private WorkflowExecutionResult generateTitlesInternal(WorkflowState state, Consumer<String> streamHandler) {
        var legacy = WorkflowStateMapper.toLegacy(state);
        AgentRun running = resumeForExecution(state.run(), WorkflowStage.TITLES_GENERATED);
        execute(running, WorkflowStage.TITLES_GENERATED,
                () -> graphExecutor.executeTitles(legacy, streamHandler));
        return new WorkflowExecutionResult(WorkflowStateMapper.fromLegacy(
                running.transitionTo(AgentRunStatus.WAITING_FOR_APPROVAL, Instant.now()), legacy),
                WorkflowStage.TITLES_GENERATED);
    }

    @Override
    public WorkflowExecutionResult generateOutline(WorkflowState state, Consumer<String> streamHandler) {
        return metricsCollector.measure(state.run().runId(), WorkflowStage.OUTLINE_GENERATED,
                () -> generateOutlineInternal(state, streamHandler));
    }

    private WorkflowExecutionResult generateOutlineInternal(WorkflowState state, Consumer<String> streamHandler) {
        requireSelectedTitle(state, WorkflowStage.OUTLINE_GENERATED);
        var legacy = WorkflowStateMapper.toLegacy(state);
        AgentRun running = resumeForExecution(state.run(), WorkflowStage.OUTLINE_GENERATED);
        execute(running, WorkflowStage.OUTLINE_GENERATED,
                () -> graphExecutor.executeOutline(legacy, streamHandler));
        return new WorkflowExecutionResult(WorkflowStateMapper.fromLegacy(
                running.transitionTo(AgentRunStatus.WAITING_FOR_APPROVAL, Instant.now()), legacy),
                WorkflowStage.OUTLINE_GENERATED);
    }

    @Override
    public WorkflowExecutionResult generateContent(WorkflowState state, Consumer<String> streamHandler) {
        return metricsCollector.measure(state.run().runId(), WorkflowStage.ARTICLE_COMPLETED,
                () -> generateContentInternal(state, streamHandler));
    }

    private WorkflowExecutionResult generateContentInternal(WorkflowState state, Consumer<String> streamHandler) {
        if (state.draft().outline() == null) {
            throw invalidState(state.run(), WorkflowStage.ARTICLE_COMPLETED,
                    "An approved outline is required before content generation");
        }
        var legacy = WorkflowStateMapper.toLegacy(state);
        AgentRun running = resumeForExecution(state.run(), WorkflowStage.ARTICLE_COMPLETED);
        execute(running, WorkflowStage.ARTICLE_COMPLETED,
                () -> graphExecutor.executeContent(legacy, streamHandler));
        return new WorkflowExecutionResult(WorkflowStateMapper.fromLegacy(
                running.transitionTo(AgentRunStatus.COMPLETED, Instant.now()), legacy),
                WorkflowStage.ARTICLE_COMPLETED);
    }

    private AgentRun resumeForExecution(AgentRun run, WorkflowStage stage) {
        if (run.status() != AgentRunStatus.PENDING && run.status() != AgentRunStatus.WAITING_FOR_APPROVAL
                && run.status() != AgentRunStatus.PAUSED) {
            throw invalidState(run, stage,
                    "Run is not ready for execution: " + run.status());
        }
        return run.transitionTo(AgentRunStatus.RUNNING, Instant.now());
    }

    private void requireSelectedTitle(WorkflowState state, WorkflowStage stage) {
        if (state.draft().selectedTitle() == null) {
            throw invalidState(state.run(), stage, "A selected title is required before outline generation");
        }
    }

    private void execute(AgentRun run, WorkflowStage stage, Runnable operation) {
        try {
            operation.run();
        } catch (WorkflowExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WorkflowExecutionException(new WorkflowError(
                    WorkflowErrorCode.GRAPH_EXECUTION, run.runId(), stage,
                    "Workflow graph execution failed", true), exception);
        }
    }

    private WorkflowExecutionException invalidState(AgentRun run, WorkflowStage stage, String message) {
        return new WorkflowExecutionException(new WorkflowError(
                WorkflowErrorCode.INVALID_STATE, run.runId(), stage, message, false), null);
    }
}
