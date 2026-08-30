package com.passage.agent.agent.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passage.agent.agent.checkpoint.NodeExecutionOutcome;
import com.passage.agent.agent.research.ResearchBundle;
import com.passage.agent.agent.research.ResearchSource;
import com.passage.agent.agent.research.ResearchSourceStatus;
import com.passage.agent.agent.supervisor.SupervisorScheduler;
import com.passage.agent.service.AgentNodeExecutionService;
import com.passage.agent.service.AgentRunService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParallelSectionWritingUseCaseTest {
    private final ResearchBundle research = new ResearchBundle("research", List.of(
            new ResearchSource("source-1", "https://example.com/one", "One", null, Instant.EPOCH,
                    "hash", "summary", "query", ResearchSourceStatus.VERIFIED)), List.of(), List.of("none"));

    @Test
    void createsBoundedSectionChildRunsAndMergesTheirSnapshotsStably() {
        AgentRunService runs = mock(AgentRunService.class);
        AgentNodeExecutionService nodes = mock(AgentNodeExecutionService.class);
        when(nodes.executeOnce(anyString(), anyString(), anyLong(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") Supplier<String> action = invocation.getArgument(3, Supplier.class);
            String snapshot = action.get();
            return new NodeExecutionOutcome("key", false, snapshot);
        });
        ParallelSectionWritingUseCase useCase = new ParallelSectionWritingUseCase(
                new SupervisorScheduler(), runs, nodes, new ObjectMapper());
        AtomicInteger calls = new AtomicInteger();

        var result = useCase.execute("parent", 2L, 2, research, List.of(
                task(2, "third"), task(0, "first"), task(1, "second")), request -> {
            calls.incrementAndGet();
            return new SectionDraft(request.task().sectionIndex(), request.task().id(), request.task().heading(), List.of("source-1"));
        });

        assertThat(result.drafts()).extracting(SectionDraft::sectionId).containsExactly("first", "second", "third");
        assertThat(result.childRunIds()).containsOnlyKeys("first", "second", "third");
        assertThat(calls).hasValue(3);
        verify(runs, times(3)).createChildRun(anyString(), anyString());
        verify(nodes, times(3)).executeOnce(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void reusesCompletedSectionSnapshotWithoutCallingWriterAgain() throws Exception {
        AgentRunService runs = mock(AgentRunService.class);
        AgentNodeExecutionService nodes = mock(AgentNodeExecutionService.class);
        String stored = new ObjectMapper().writeValueAsString(new SectionDraft(0, "first", "stored", List.of("source-1")));
        when(nodes.executeOnce(anyString(), anyString(), anyLong(), any()))
                .thenReturn(new NodeExecutionOutcome("key", true, stored));
        ParallelSectionWritingUseCase useCase = new ParallelSectionWritingUseCase(
                new SupervisorScheduler(), runs, nodes, new ObjectMapper());

        var result = useCase.execute("parent", 2L, 1, research, List.of(task(0, "first")), request -> {
            throw new AssertionError("reused node snapshot must not invoke the Writer");
        });

        assertThat(result.drafts()).singleElement().extracting(SectionDraft::markdown).isEqualTo("stored");
    }

    private static SectionTask task(int index, String id) {
        return new SectionTask(index, id, "Heading " + id, "Write " + id, List.of("source-1"));
    }
}
