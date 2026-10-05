package com.passage.agent.agent.harness;

import com.passage.agent.agent.api.WorkflowExecutionResult;
import com.passage.agent.agent.api.WorkflowRunner;
import com.passage.agent.agent.api.WorkflowStage;
import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import com.passage.agent.agent.fixture.ArticleWorkflowFixture;
import com.passage.agent.agent.review.QualityGateDecision;
import com.passage.agent.agent.review.ReviewReport;
import com.passage.agent.agent.review.ReviewType;
import com.passage.agent.agent.state.WorkflowState;
import com.passage.agent.agent.supervisor.HumanDecision;
import com.passage.agent.agent.supervisor.PlanAction;
import com.passage.agent.agent.supervisor.PlanFeedback;
import com.passage.agent.agent.supervisor.PlanReplanner;
import com.passage.agent.agent.supervisor.ReplanResult;
import com.passage.agent.agent.supervisor.SupervisorPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AgentHarnessContractTest {

    @Test
    void delegatesEveryTaskTypeWithoutAddingExecutionLogic() {
        WorkflowRunner runner = mock(WorkflowRunner.class);
        StateManager stateManager = mock(StateManager.class);
        PlanReplanner planner = mock(PlanReplanner.class);
        DefaultAgentHarness harness = new DefaultAgentHarness(runner, stateManager, planner);
        WorkflowState state = ArticleWorkflowFixture.workflowState("run-h1", "task-h1");
        Consumer<String> streamHandler = ignored -> { };
        WorkflowExecutionResult expected = new WorkflowExecutionResult(state, WorkflowStage.TITLES_GENERATED);
        when(runner.generateTitles(state, streamHandler)).thenReturn(expected);
        when(runner.generateOutline(state, streamHandler)).thenReturn(expected);
        when(runner.generateContent(state, streamHandler)).thenReturn(expected);

        assertThat(harness.run(new AgentTask(state, AgentTask.Type.GENERATE_TITLES), streamHandler))
                .isSameAs(expected);
        assertThat(harness.run(new AgentTask(state, AgentTask.Type.GENERATE_OUTLINE), streamHandler))
                .isSameAs(expected);
        assertThat(harness.run(new AgentTask(state, AgentTask.Type.GENERATE_CONTENT), streamHandler))
                .isSameAs(expected);

        verify(runner).generateTitles(state, streamHandler);
        verify(runner).generateOutline(state, streamHandler);
        verify(runner).generateContent(state, streamHandler);
        verifyNoMoreInteractions(runner, stateManager, planner);
    }

    @Test
    void exposesResumeAndCancelThroughTheExistingStateManager() {
        WorkflowRunner runner = mock(WorkflowRunner.class);
        StateManager stateManager = mock(StateManager.class);
        PlanReplanner planner = mock(PlanReplanner.class);
        DefaultAgentHarness harness = new DefaultAgentHarness(runner, stateManager, planner);
        Consumer<WorkflowCheckpoint> nextNode = ignored -> { };
        when(stateManager.cancelRun("run-h1")).thenReturn(true);

        harness.resume("checkpoint-h1", nextNode);
        assertThat(harness.cancel("run-h1")).isTrue();

        verify(stateManager).resumeCheckpoint("checkpoint-h1", nextNode);
        verify(stateManager).cancelRun("run-h1");
        verifyNoMoreInteractions(runner, stateManager, planner);
    }

    @Test
    void delegatesReplanAndHumanDecisionsToThePlanner() {
        WorkflowRunner runner = mock(WorkflowRunner.class);
        StateManager stateManager = mock(StateManager.class);
        PlanReplanner planner = mock(PlanReplanner.class);
        DefaultAgentHarness harness = new DefaultAgentHarness(runner, stateManager, planner);
        SupervisorPlan plan = new SupervisorPlan(false, 1, List.of());
        PlanFeedback feedback = PlanFeedback.reviewer(
                new ReviewReport(ReviewType.FACT, 90, List.of()),
                new ReviewReport(ReviewType.STYLE, 90, List.of()),
                new QualityGateDecision(QualityGateDecision.Decision.ACCEPT, 0, List.of()));
        HumanDecision human = new HumanDecision(HumanDecision.Type.MODIFY, "shorten the title");
        ReplanResult expected = new ReplanResult(PlanAction.CONTINUE, plan, List.of());
        when(planner.replan(plan, feedback)).thenReturn(expected);
        when(planner.replan(plan, PlanFeedback.human(human))).thenReturn(expected);

        assertThat(harness.replan(plan, feedback)).isSameAs(expected);
        assertThat(harness.handleHumanDecision(plan, human)).isSameAs(expected);

        verify(planner).replan(plan, feedback);
        verify(planner).replan(plan, PlanFeedback.human(human));
        verifyNoMoreInteractions(runner, stateManager, planner);
    }
}
