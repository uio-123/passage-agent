package com.passage.agent.agent.metrics;

import com.passage.agent.agent.api.WorkflowErrorCode;
import com.passage.agent.agent.api.WorkflowExecutionException;
import com.passage.agent.agent.api.WorkflowExecutionResult;
import com.passage.agent.agent.api.WorkflowStage;
import com.passage.agent.agent.run.AgentRunStatus;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.passage.agent.service.AgentModelCallMetricService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Keeps a small, process-local baseline for repeatable workflow tests and local
 * diagnosis. It deliberately does not claim to be production telemetry.
 */
@Component
public class WorkflowMetricsCollector {

    private final ConcurrentMap<String, MutableExecutionMetrics> executions = new ConcurrentHashMap<>();
    private final ThreadLocal<StageScope> activeStage = new ThreadLocal<>();
    private final AgentModelCallMetricService modelCallMetrics;

    /** Compatibility constructor for isolated workflow unit tests; production wiring persists measurements. */
    public WorkflowMetricsCollector() {
        this((runId, stage, attempt, callIndex, measurement) -> { });
    }

    @Autowired
    public WorkflowMetricsCollector(AgentModelCallMetricService modelCallMetrics) {
        this.modelCallMetrics = modelCallMetrics;
    }

    public WorkflowExecutionResult measure(String runId, WorkflowStage stage,
                                           Supplier<WorkflowExecutionResult> operation) {
        StageScope previous = activeStage.get();
        StageScope scope = new StageScope();
        activeStage.set(scope);
        long startedAt = System.nanoTime();
        try {
            WorkflowExecutionResult result = operation.get();
            record(runId, new WorkflowStageMetrics(stage, elapsed(startedAt), scope.modelCallCount,
                    result.state().run().status(), null));
            persistModelCalls(runId, stage, scope);
            return result;
        } catch (WorkflowExecutionException exception) {
            record(runId, new WorkflowStageMetrics(stage, elapsed(startedAt), scope.modelCallCount,
                    AgentRunStatus.FAILED, exception.error().code()));
            persistModelCalls(runId, stage, scope);
            throw exception;
        } finally {
            if (previous == null) {
                activeStage.remove();
            } else {
                activeStage.set(previous);
            }
        }
    }

    /** Called only by the project-owned AiModelPort decorator. */
    public void recordModelCall() {
        StageScope scope = activeStage.get();
        if (scope != null) {
            scope.modelCallCount++;
        }
    }

    /** Accepts metadata extracted from the actual model response; null fields mean the provider did not report them. */
    public void recordModelMeasurement(ModelCallMeasurement measurement) {
        StageScope scope = activeStage.get();
        if (scope != null) {
            scope.measurements.add(measurement);
        }
    }

    private void persistModelCalls(String runId, WorkflowStage stage, StageScope scope) {
        for (int index = 0; index < scope.measurements.size(); index++) {
            modelCallMetrics.record(runId, stage.name(), 1, index + 1, scope.measurements.get(index));
        }
    }

    public Optional<WorkflowExecutionMetrics> find(String runId) {
        MutableExecutionMetrics metrics = executions.get(runId);
        return metrics == null ? Optional.empty() : Optional.of(metrics.snapshot(runId));
    }

    private void record(String runId, WorkflowStageMetrics stageMetrics) {
        executions.computeIfAbsent(runId, ignored -> new MutableExecutionMetrics()).add(stageMetrics);
    }

    private Duration elapsed(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private static final class StageScope {
        private int modelCallCount;
        private final List<ModelCallMeasurement> measurements = new ArrayList<>();
    }

    private static final class MutableExecutionMetrics {
        private final List<WorkflowStageMetrics> stages = new ArrayList<>();

        synchronized void add(WorkflowStageMetrics stage) {
            stages.add(stage);
        }

        synchronized WorkflowExecutionMetrics snapshot(String runId) {
            WorkflowStageMetrics latest = stages.getLast();
            return new WorkflowExecutionMetrics(runId, stages, latest.resultStatus(), latest.errorCode());
        }
    }
}
