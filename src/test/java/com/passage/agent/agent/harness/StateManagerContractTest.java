package com.passage.agent.agent.harness;

import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import com.passage.agent.agent.run.AgentRun;
import com.passage.agent.agent.run.AgentRunStatus;
import com.passage.agent.model.entity.AgentRunRecord;
import com.passage.agent.service.AgentCheckpointService;
import com.passage.agent.service.AgentRunService;
import com.passage.agent.service.WorkflowRecoveryService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class StateManagerContractTest {

    @Test
    void delegatesRunPersistenceWithoutIntroducingAnotherStateStore() {
        AgentRunService runs = mock(AgentRunService.class);
        AgentCheckpointService checkpoints = mock(AgentCheckpointService.class);
        WorkflowRecoveryService recovery = mock(WorkflowRecoveryService.class);
        DefaultStateManager stateManager = new DefaultStateManager(runs, checkpoints, recovery);
        AgentRunRecord record = new AgentRunRecord();
        AgentRun run = AgentRun.root("run-h1", Instant.parse("2026-10-05T08:00:00Z"));
        when(runs.createRootRun("task-h1")).thenReturn(record);
        when(runs.createChildRun("child-h1", "run-h1")).thenReturn(record);
        when(runs.getByRunId("run-h1")).thenReturn(record);
        when(runs.getDomain("run-h1")).thenReturn(run);

        assertThat(stateManager.createRootRun("task-h1")).isSameAs(record);
        assertThat(stateManager.createChildRun("child-h1", "run-h1")).isSameAs(record);
        assertThat(stateManager.loadRunRecord("run-h1")).isSameAs(record);
        assertThat(stateManager.loadRun("run-h1")).isSameAs(run);
        stateManager.saveRun(run, "titles");
        stateManager.markFailed("run-h1", "controlled");

        verify(runs).createRootRun("task-h1");
        verify(runs).createChildRun("child-h1", "run-h1");
        verify(runs).getByRunId("run-h1");
        verify(runs).getDomain("run-h1");
        verify(runs).sync(run, "titles");
        verify(runs).markFailed("run-h1", "controlled");
        verifyNoMoreInteractions(runs, checkpoints, recovery);
    }

    @Test
    void delegatesCheckpointRecoveryAndCancellation() {
        AgentRunService runs = mock(AgentRunService.class);
        AgentCheckpointService checkpoints = mock(AgentCheckpointService.class);
        WorkflowRecoveryService recovery = mock(WorkflowRecoveryService.class);
        DefaultStateManager stateManager = new DefaultStateManager(runs, checkpoints, recovery);
        WorkflowCheckpoint checkpoint = mock(WorkflowCheckpoint.class);
        Consumer<WorkflowCheckpoint> nextNode = ignored -> { };
        when(checkpoints.persistCheckpoint(
                "run-h1", 3L, "checkpoint-h1", "outline", "{}", AgentRunStatus.WAITING_FOR_APPROVAL))
                .thenReturn(checkpoint);
        when(checkpoints.findReadyCheckpoint("run-h1", "outline")).thenReturn(checkpoint);
        when(recovery.cancel("run-h1")).thenReturn(true);

        assertThat(stateManager.createCheckpoint(
                "run-h1", 3L, "checkpoint-h1", "outline", "{}", AgentRunStatus.WAITING_FOR_APPROVAL))
                .isSameAs(checkpoint);
        assertThat(stateManager.findReadyCheckpoint("run-h1", "outline")).isSameAs(checkpoint);
        stateManager.resumeCheckpoint("checkpoint-h1", nextNode);
        assertThat(stateManager.cancelRun("run-h1")).isTrue();

        verify(checkpoints).persistCheckpoint(
                "run-h1", 3L, "checkpoint-h1", "outline", "{}", AgentRunStatus.WAITING_FOR_APPROVAL);
        verify(checkpoints).findReadyCheckpoint("run-h1", "outline");
        verify(recovery).resume("checkpoint-h1", nextNode);
        verify(recovery).cancel("run-h1");
        verifyNoMoreInteractions(runs, checkpoints, recovery);
    }
}
