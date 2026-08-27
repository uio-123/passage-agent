package com.yupi.template.agent.metrics;

import com.yupi.template.agent.api.WorkflowErrorCode;
import com.yupi.template.agent.api.WorkflowExecutionException;
import com.yupi.template.agent.api.WorkflowExecutionResult;
import com.yupi.template.agent.api.WorkflowStage;
import com.yupi.template.agent.run.AgentRunStatus;
import org.springframework.stereotype.Component;

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
            return result;
        } catch (WorkflowExecutionException exception) {
            record(runId, new WorkflowStageMetrics(stage, elapsed(startedAt), scope.modelCallCount,
                    AgentRunStatus.FAILED, exception.error().code()));
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
