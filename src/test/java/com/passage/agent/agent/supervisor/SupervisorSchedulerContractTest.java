package com.passage.agent.agent.supervisor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SupervisorSchedulerContractTest {

    private final SupervisorScheduler scheduler = new SupervisorScheduler();

    @Test
    void choosesResearchOnlyWhenPlanRequiresIt() {
        assertThat(scheduler.initialRoute(new SupervisorPlan(true, 1, List.of())))
                .isEqualTo(ExecutionRoute.RESEARCH);
        assertThat(scheduler.initialRoute(new SupervisorPlan(false, 1, List.of())))
                .isEqualTo(ExecutionRoute.WRITE);
    }

    @Test
    void boundsParallelWritersAndMergesBySectionIndexNotCompletionOrder() throws Exception {
        SupervisorPlan plan = new SupervisorPlan(false, 2, List.of(
                new SubtaskSpec(2, "third", "write third"),
                new SubtaskSpec(0, "first", "write first"),
                new SubtaskSpec(1, "second", "write second")
        ));
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        CountDownLatch firstTwoStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<List<SupervisorScheduler.SubtaskResult>> result = CompletableFuture.supplyAsync(() ->
                scheduler.executeWriters(plan, subtask -> {
                    int current = active.incrementAndGet();
                    maximumActive.accumulateAndGet(current, Math::max);
                    firstTwoStarted.countDown();
                    try {
                        if (!release.await(2, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("test writer was not released");
                        }
                        return subtask.id();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    } finally {
                        active.decrementAndGet();
                    }
                }));

        assertThat(firstTwoStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(maximumActive.get()).isEqualTo(2);
        release.countDown();

        assertThat(result.join()).extracting(SupervisorScheduler.SubtaskResult::subtaskId)
                .containsExactly("first", "second", "third");
        assertThat(maximumActive.get()).isLessThanOrEqualTo(2);
    }
}
