package com.yupi.template.agent.supervisor;

import com.yupi.template.service.AgentRunService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupervisorWorkflowServiceTest {

    @Test
    void skipsResearchChildWhenPlanDoesNotRequireResearch() {
        AgentRunService runs = mock(AgentRunService.class);
        when(runs.getByRunId(anyString())).thenReturn(null);
        SupervisorWorkflowService service = new SupervisorWorkflowService(
                new SupervisorPlanValidator(), new SupervisorScheduler(), runs);
        AtomicInteger researchCalls = new AtomicInteger();

        var result = service.execute("parent", new SupervisorPlan(false, 1, List.of(
                new SubtaskSpec(0, "write", "draft")
        )), researchCalls::incrementAndGet, subtask -> "content");

        assertThat(result.route()).isEqualTo(ExecutionRoute.WRITE);
        assertThat(result.children()).extracting(SupervisorWorkflowService.ChildExecution::nodeId)
                .containsExactly("write");
        assertThat(researchCalls).hasValue(0);
        verify(runs).createChildRun(anyString(), org.mockito.ArgumentMatchers.eq("parent"));
    }

    @Test
    void runsResearchBeforeDependentWriterAndCreatesSeparateChildren() {
        AgentRunService runs = mock(AgentRunService.class);
        when(runs.getByRunId(anyString())).thenReturn(null);
        SupervisorWorkflowService service = new SupervisorWorkflowService(
                new SupervisorPlanValidator(), new SupervisorScheduler(), runs);
        AtomicInteger researchCalls = new AtomicInteger();
        AtomicInteger writerCalls = new AtomicInteger();
        var plan = new SupervisorPlan(true, 2, 2, Set.of(), List.of(
                new SubtaskSpec(0, "facts", "facts"),
                new SubtaskSpec(1, "write", "draft", List.of("facts"), Set.of())
        ));

        var result = service.execute("parent", plan, researchCalls::incrementAndGet,
                subtask -> subtask.id() + '-' + writerCalls.incrementAndGet());

        assertThat(result.route()).isEqualTo(ExecutionRoute.RESEARCH);
        assertThat(researchCalls).hasValue(1);
        assertThat(result.children()).extracting(SupervisorWorkflowService.ChildExecution::nodeId)
                .containsExactly("research", "facts", "write");
        assertThat(result.children()).extracting(SupervisorWorkflowService.ChildExecution::content)
                .containsExactly(null, "facts-1", "write-2");
        verify(runs, org.mockito.Mockito.times(3)).createChildRun(anyString(), org.mockito.ArgumentMatchers.eq("parent"));
    }
}
