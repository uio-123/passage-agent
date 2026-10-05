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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Keeps a small, process-local baseline for repeatable workflow tests and local
 * diagnosis. It deliberately does not claim to be production telemetry.
 */
@Component
public class WorkflowMetricsCollector {

    private final ConcurrentMap<String, MutableExecutionMetrics> executions = new ConcurrentHashMap<>();
    private final InheritableThreadLocal<StageScope> activeStage = new InheritableThreadLocal<>();
    private final AgentModelCallMetricService modelCallMetrics;

    /** Compatibility constructor for isolated workflow unit tests; production wiring persists measurements. */
    public WorkflowMetricsCollector() {
        this(new AgentModelCallMetricService() {
            @Override
            public void record(String runId, String stage, int attempt, int callIndex,
                               com.passage.agent.agent.metrics.ModelCallMeasurement measurement) {
            }

            @Override
            public List<com.passage.agent.model.entity.AgentModelCallMetricRecord> listByRunId(String runId) {
                return List.of();
            }
        });
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
            record(runId, new WorkflowStageMetrics(stage, elapsed(startedAt), scope.modelCallCount(),
                    result.state().run().status(), null));
            persistModelCalls(runId, stage, scope);
            return result;
        } catch (WorkflowExecutionException exception) {
            record(runId, new WorkflowStageMetrics(stage, elapsed(startedAt), scope.modelCallCount(),
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
            scope.incrementModelCall();
        }
    }

    /** Accepts metadata extracted from the actual model response; null fields mean the provider did not report them. */
    public void recordModelMeasurement(ModelCallMeasurement measurement) {
        StageScope scope = activeStage.get();
        if (scope != null) {
            scope.add(measurement);
        }
    }

    private void persistModelCalls(String runId, WorkflowStage stage, StageScope scope) {
        List<ModelCallMeasurement> measurements = scope.snapshotMeasurements();
        for (int index = 0; index < measurements.size(); index++) {
            modelCallMetrics.record(runId, stage.name(), 1, index + 1, measurements.get(index));
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
        private final AtomicInteger modelCallCount = new AtomicInteger();
        private final List<ModelCallMeasurement> measurements = Collections.synchronizedList(new ArrayList<>());

        int incrementModelCall() {
            return modelCallCount.incrementAndGet();
        }

        int modelCallCount() {
            return modelCallCount.get();
        }

        void add(ModelCallMeasurement measurement) {
            measurements.add(measurement);
        }

        List<ModelCallMeasurement> snapshotMeasurements() {
            synchronized (measurements) {
                return List.copyOf(measurements);
            }
        }
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
