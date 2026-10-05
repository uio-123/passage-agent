package com.passage.agent.agent.harness;

import com.passage.agent.agent.api.WorkflowExecutionResult;
import com.passage.agent.agent.api.WorkflowRunner;
import com.passage.agent.agent.api.WorkflowStage;
import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import com.passage.agent.agent.fixture.ArticleWorkflowFixture;
import com.passage.agent.agent.state.WorkflowState;
import org.junit.jupiter.api.Test;

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
        DefaultAgentHarness harness = new DefaultAgentHarness(runner, stateManager);
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
        verifyNoMoreInteractions(runner, stateManager);
    }

    @Test
    void exposesResumeAndCancelThroughTheExistingStateManager() {
        WorkflowRunner runner = mock(WorkflowRunner.class);
        StateManager stateManager = mock(StateManager.class);
        DefaultAgentHarness harness = new DefaultAgentHarness(runner, stateManager);
        Consumer<WorkflowCheckpoint> nextNode = ignored -> { };
        when(stateManager.cancelRun("run-h1")).thenReturn(true);

        harness.resume("checkpoint-h1", nextNode);
        assertThat(harness.cancel("run-h1")).isTrue();

        verify(stateManager).resumeCheckpoint("checkpoint-h1", nextNode);
        verify(stateManager).cancelRun("run-h1");
        verifyNoMoreInteractions(runner, stateManager);
    }
}
