package com.yupi.template.service.impl;

import com.yupi.template.agent.checkpoint.CheckpointStatus;
import com.yupi.template.agent.checkpoint.WorkflowCheckpoint;
import com.yupi.template.service.AgentCheckpointService;
import com.yupi.template.service.AgentRunService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowRecoveryServiceImplTest {

    @Test
    void consumesAClaimOnlyAfterTheNextNodeSucceeds() {
        AgentCheckpointService checkpoints = mock(AgentCheckpointService.class);
        WorkflowRecoveryServiceImpl service = new WorkflowRecoveryServiceImpl(checkpoints, mock(AgentRunService.class));
        when(checkpoints.claimForResume("checkpoint")).thenReturn(checkpoint());
        AtomicInteger executions = new AtomicInteger();

        service.resume("checkpoint", ignored -> executions.incrementAndGet());

        assertThat(executions).hasValue(1);
        verify(checkpoints).consumeClaim("checkpoint");
    }

    @Test
    void releasesTheClaimWhenTheNextNodeFailsAndCancellationCancelsCheckpoints() {
        AgentCheckpointService checkpoints = mock(AgentCheckpointService.class);
        AgentRunService runs = mock(AgentRunService.class);
        WorkflowRecoveryServiceImpl service = new WorkflowRecoveryServiceImpl(checkpoints, runs);
        when(checkpoints.claimForResume("checkpoint")).thenReturn(checkpoint());

        assertThatThrownBy(() -> service.resume("checkpoint", ignored -> { throw new IllegalStateException("retry"); }))
                .isInstanceOf(IllegalStateException.class);
        verify(checkpoints).releaseClaim("checkpoint");

        when(runs.cancel("run-1")).thenReturn(true);
        assertThat(service.cancel("run-1")).isTrue();
        verify(checkpoints).cancelPendingForRun("run-1");
    }

    private WorkflowCheckpoint checkpoint() {
        Instant now = Instant.parse("2026-08-28T08:00:00Z");
        return new WorkflowCheckpoint("checkpoint", "run-1", "outline", 1L, "{}",
                CheckpointStatus.CLAIMED, now, now);
    }
}
