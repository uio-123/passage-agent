package com.passage.agent.agent.workflow;

import com.passage.agent.agent.ArticleAgentOrchestrator;
import com.passage.agent.agent.config.AgentConfig;
import com.passage.agent.agent.graph.ArticleWorkflowExecutor;
import com.passage.agent.agent.api.WorkflowRunner;
import com.passage.agent.agent.api.WorkflowError;
import com.passage.agent.agent.api.WorkflowErrorCode;
import com.passage.agent.agent.api.WorkflowExecutionException;
import com.passage.agent.agent.api.WorkflowExecutionResult;
import com.passage.agent.agent.api.WorkflowStage;
import com.passage.agent.agent.metrics.WorkflowMetricsCollector;
import com.passage.agent.agent.run.AgentRun;
import com.passage.agent.agent.run.AgentRunStatus;
import com.passage.agent.agent.state.WorkflowState;
import com.passage.agent.agent.state.WorkflowStateMapper;
import com.passage.agent.agent.state.WorkflowStateReducer;
import com.passage.agent.agent.review.QualityGateDecision;
import com.passage.agent.model.entity.AgentRunRecord;
import com.passage.agent.service.AgentRunService;
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
    private final boolean qualityLoopEnabled;
    private final int qualityLoopMaxConcurrency;
    private final ApprovedOutlineWritingRequestFactory approvedOutlineFactory;
    private final P3ContentWorkflow p3ContentWorkflow;
    private final AgentRunService agentRunService;
    private final P3ContentCheckpointPublisher p3CheckpointPublisher;

    public ArticleWorkflowRunner(ArticleAgentOrchestrator orchestrator) {
        this(orchestrator, new WorkflowMetricsCollector());
    }

    public ArticleWorkflowRunner(ArticleAgentOrchestrator orchestrator, WorkflowMetricsCollector metricsCollector) {
        this((ArticleWorkflowExecutor) orchestrator, metricsCollector);
    }

    public ArticleWorkflowRunner(ArticleWorkflowExecutor graphExecutor, WorkflowMetricsCollector metricsCollector) {
        this(graphExecutor, metricsCollector, false, 1, null, null, null, null);
    }

    @Autowired
    public ArticleWorkflowRunner(ArticleWorkflowExecutor graphExecutor, WorkflowMetricsCollector metricsCollector, AgentConfig agentConfig,
                                 ApprovedOutlineWritingRequestFactory approvedOutlineFactory, P3ContentWorkflow p3ContentWorkflow,
                                 AgentRunService agentRunService, P3ContentCheckpointPublisher p3CheckpointPublisher) {
        this(graphExecutor, metricsCollector, agentConfig.isQualityLoopEnabled(), agentConfig.getQualityLoopMaxConcurrency(),
                approvedOutlineFactory, p3ContentWorkflow, agentRunService, p3CheckpointPublisher);
    }

    ArticleWorkflowRunner(ArticleWorkflowExecutor graphExecutor, WorkflowMetricsCollector metricsCollector, boolean qualityLoopEnabled,
                          int qualityLoopMaxConcurrency, ApprovedOutlineWritingRequestFactory approvedOutlineFactory,
                          P3ContentWorkflow p3ContentWorkflow, AgentRunService agentRunService,
                          P3ContentCheckpointPublisher p3CheckpointPublisher) {
        this.graphExecutor = graphExecutor;
        this.metricsCollector = metricsCollector;
        this.qualityLoopEnabled = qualityLoopEnabled;
        this.qualityLoopMaxConcurrency = qualityLoopMaxConcurrency;
        this.approvedOutlineFactory = approvedOutlineFactory;
        this.p3ContentWorkflow = p3ContentWorkflow;
        this.agentRunService = agentRunService;
        this.p3CheckpointPublisher = p3CheckpointPublisher;
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
        if (qualityLoopEnabled) return generateP3Content(state);
        var legacy = WorkflowStateMapper.toLegacy(state);
        AgentRun running = resumeForExecution(state.run(), WorkflowStage.ARTICLE_COMPLETED);
        execute(running, WorkflowStage.ARTICLE_COMPLETED,
                () -> graphExecutor.executeContent(legacy, streamHandler));
        return new WorkflowExecutionResult(WorkflowStateMapper.fromLegacy(
                running.transitionTo(AgentRunStatus.COMPLETED, Instant.now()), legacy),
                WorkflowStage.ARTICLE_COMPLETED);
    }

    private WorkflowExecutionResult generateP3Content(WorkflowState state) {
        if (approvedOutlineFactory == null || p3ContentWorkflow == null || agentRunService == null || p3CheckpointPublisher == null) {
            throw invalidState(state.run(), WorkflowStage.CONTENT_QUALITY_ACCEPTED, "P3 quality-loop dependencies are unavailable");
        }
        AgentRunRecord persisted = agentRunService.getByRunId(state.run().runId());
        if (persisted == null) persisted = agentRunService.createRootRun(state.run().runId());
        long stateVersion = persisted.getStateVersion() == null ? 0L : persisted.getStateVersion();
        AgentRun running = resumeForExecution(state.run(), WorkflowStage.CONTENT_QUALITY_ACCEPTED);
        agentRunService.sync(running, "content-quality");
        try {
            ApprovedOutlineWritingResult result = p3ContentWorkflow.execute(
                    approvedOutlineFactory.create(state, stateVersion, null), qualityLoopMaxConcurrency);
            if (result.decision().decision() != QualityGateDecision.Decision.ACCEPT) {
                throw new WorkflowExecutionException(new WorkflowError(WorkflowErrorCode.QUALITY_REJECTED, state.run().runId(),
                        WorkflowStage.CONTENT_QUALITY_ACCEPTED, "Quality gate rejected article content", false), null);
            }
            p3CheckpointPublisher.publish(state, stateVersion, result);
            WorkflowState accepted = WorkflowStateReducer.withRun(WorkflowStateReducer.withContent(state, result.markdown()), running);
            return new WorkflowExecutionResult(accepted, WorkflowStage.CONTENT_QUALITY_ACCEPTED);
        } catch (WorkflowExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WorkflowExecutionException(new WorkflowError(WorkflowErrorCode.QUALITY_EXECUTION, state.run().runId(),
                    WorkflowStage.CONTENT_QUALITY_ACCEPTED, "P3 quality workflow execution failed", true), exception);
        }
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
