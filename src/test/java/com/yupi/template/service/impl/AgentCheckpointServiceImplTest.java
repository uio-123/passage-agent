package com.yupi.template.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.yupi.template.agent.checkpoint.CheckpointStatus;
import com.yupi.template.agent.run.AgentRun;
import com.yupi.template.agent.run.AgentRunStatus;
import com.yupi.template.model.entity.AgentCheckpointRecord;
import com.yupi.template.model.entity.AgentRunRecord;
import com.yupi.template.service.AgentRunService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentCheckpointServiceImplTest {

    @Test
    void persistsTheNextStateVersionOnlyAfterTheRunCanAdvance() {
        AgentRunService runService = mock(AgentRunService.class);
        AgentCheckpointServiceImpl service = spy(new AgentCheckpointServiceImpl(runService));
        AgentRunRecord run = run("run-1", 3L, AgentRunStatus.RUNNING);
        when(runService.getByRunId("run-1")).thenReturn(run);
        when(runService.getDomain("run-1")).thenReturn(domainRun(AgentRunStatus.RUNNING));
        when(runService.update(any(AgentRunRecord.class), any(QueryWrapper.class))).thenReturn(true);
        doReturn(true).when(service).save(any(AgentCheckpointRecord.class));

        var checkpoint = service.persistCheckpoint(
                "run-1", 3L, "checkpoint-4", "outline", "{\"version\":4}",
                AgentRunStatus.WAITING_FOR_APPROVAL);

        assertThat(checkpoint.stateVersion()).isEqualTo(4L);
        assertThat(checkpoint.status()).isEqualTo(CheckpointStatus.READY);
        verify(runService).update(any(AgentRunRecord.class), any(QueryWrapper.class));
    }

    @Test
    void rejectsASecondResumerWhenTheCheckpointCompareAndSetLoses() {
        AgentRunService runService = mock(AgentRunService.class);
        AgentCheckpointServiceImpl service = spy(new AgentCheckpointServiceImpl(runService));
        AgentCheckpointRecord checkpoint = AgentCheckpointRecord.builder()
                .checkpointId("checkpoint-4").runId("run-1").nodeId("outline")
                .stateVersion(4L).stateSnapshot("{}").status(CheckpointStatus.READY.name())
                .createTime(LocalDateTime.now()).build();
        doReturn(checkpoint).when(service).getOne(any(QueryWrapper.class));
        doReturn(false).when(service).update(any(AgentCheckpointRecord.class), any(QueryWrapper.class));

        assertThatIllegalStateException().isThrownBy(() -> service.claimForResume("checkpoint-4"));
    }

    @Test
    void refusesToResumeATerminalRunEvenAfterTheCheckpointWasClaimed() {
        AgentRunService runService = mock(AgentRunService.class);
        AgentCheckpointServiceImpl service = spy(new AgentCheckpointServiceImpl(runService));
        AgentCheckpointRecord checkpoint = AgentCheckpointRecord.builder()
                .checkpointId("checkpoint-4").runId("run-1").nodeId("outline")
                .stateVersion(4L).stateSnapshot("{}").status(CheckpointStatus.READY.name())
                .createTime(LocalDateTime.now()).build();
        doReturn(checkpoint).when(service).getOne(any(QueryWrapper.class));
        doReturn(true).when(service).update(any(AgentCheckpointRecord.class), any(QueryWrapper.class));
        when(runService.getByRunId("run-1")).thenReturn(run("run-1", 4L, AgentRunStatus.CANCELLED));

        assertThatIllegalStateException().isThrownBy(() -> service.claimForResume("checkpoint-4"));
    }

    private AgentRunRecord run(String runId, long version, AgentRunStatus status) {
        return AgentRunRecord.builder().runId(runId).rootRunId(runId).taskId("task-1")
                .stateVersion(version).status(status.name()).createTime(LocalDateTime.now()).build();
    }

    private AgentRun domainRun(AgentRunStatus status) {
        Instant now = Instant.parse("2020-08-28T08:00:00Z");
        return new AgentRun("run-1", "run-1", null, status, now, now);
    }
}
