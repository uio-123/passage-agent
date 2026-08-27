package com.yupi.template.agent.supervisor;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Framework-neutral scheduling rules used by future graph nodes. It makes
 * routing, bounded parallelism and stable fan-in explicit and testable.
 */
public class SupervisorScheduler {

    public ExecutionRoute initialRoute(SupervisorPlan plan) {
        return plan.researchRequired() ? ExecutionRoute.RESEARCH : ExecutionRoute.WRITE;
    }

    public List<SubtaskResult> executeWriters(
            SupervisorPlan plan,
            Function<SubtaskSpec, String> writer
    ) {
        try (ExecutorService executor = Executors.newFixedThreadPool(plan.maxConcurrency())) {
            List<CompletableFuture<SubtaskResult>> futures = plan.subtasks().stream()
                    .map(subtask -> CompletableFuture.supplyAsync(
                            () -> new SubtaskResult(subtask.sectionIndex(), subtask.id(), writer.apply(subtask)),
                            executor))
                    .toList();

            return futures.stream()
                    .map(CompletableFuture::join)
                    .sorted(Comparator.comparingInt(SubtaskResult::sectionIndex))
                    .toList();
        }
    }

    public record SubtaskResult(int sectionIndex, String subtaskId, String content) {
    }
}
