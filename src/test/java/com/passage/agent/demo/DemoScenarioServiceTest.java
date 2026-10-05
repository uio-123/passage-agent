package com.passage.agent.demo;

import com.passage.agent.agent.event.AgentEvent;
import com.passage.agent.agent.event.AgentEventPublisher;
import com.passage.agent.agent.run.AgentRun;
import com.passage.agent.agent.run.AgentRunStatus;
import com.passage.agent.model.entity.AgentArtifactRecord;
import com.passage.agent.model.entity.AgentArticleVersionRecord;
import com.passage.agent.model.entity.AgentRunRecord;
import com.passage.agent.service.AgentArticleArtifactService;
import com.passage.agent.service.AgentEventService;
import com.passage.agent.service.AgentRunService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemoScenarioServiceTest {
    @Test
    void persistsDeterministicRunEventsAndArtifactsWithoutCallingAModel() {
        AgentRunService runs = mock(AgentRunService.class);
        AgentEventPublisher publisher = mock(AgentEventPublisher.class);
        AgentEventService events = mock(AgentEventService.class);
        AgentArticleArtifactService artifacts = mock(AgentArticleArtifactService.class);
        String runId = "demo-" + shaPrefix("scenario-1");
        AgentRunRecord completed = record(runId, "COMPLETED");
        Instant databaseClockAhead = Instant.now().plusSeconds(60);
        when(runs.getByRunId(runId)).thenReturn(null, completed);
        when(runs.getDomain(runId)).thenReturn(
                new AgentRun(runId, runId, null, AgentRunStatus.PENDING, databaseClockAhead, databaseClockAhead),
                new AgentRun(runId, runId, null, AgentRunStatus.RUNNING, databaseClockAhead, databaseClockAhead.plusSeconds(1)));
        when(artifacts.listVersions(runId)).thenReturn(List.of(new AgentArticleVersionRecord()));
        when(artifacts.listArtifacts(runId, 1)).thenReturn(List.of(new AgentArtifactRecord(), new AgentArtifactRecord(), new AgentArtifactRecord()));
        when(events.findAfter(runId, 0, 200)).thenReturn(List.of(mock(AgentEvent.class), mock(AgentEvent.class)));

        DemoScenarioResult result = new DemoScenarioService(runs, publisher, events, artifacts)
                .run("scenario-1", DemoScenarioType.RECOVERABLE_FAULT);

        assertEquals("COMPLETED", result.runStatus());
        assertEquals(1, result.versionCount());
        assertEquals(3, result.artifactCount());
        assertTrue(result.recoverySucceeded());
        assertFalse(result.reused());
        verify(runs).createRootRun(runId);
        verify(artifacts).publish(anyString(), any(), any());
    }

    @Test
    void completedScenarioIsReusedWithoutRepublishingSideEffects() {
        AgentRunService runs = mock(AgentRunService.class);
        AgentEventPublisher publisher = mock(AgentEventPublisher.class);
        AgentEventService events = mock(AgentEventService.class);
        AgentArticleArtifactService artifacts = mock(AgentArticleArtifactService.class);
        String runId = "demo-" + shaPrefix("same-scenario");
        AgentRunRecord completed = record(runId, "COMPLETED");
        when(runs.getByRunId(runId)).thenReturn(completed, completed);
        when(artifacts.listVersions(runId)).thenReturn(List.of(new AgentArticleVersionRecord()));
        when(artifacts.listArtifacts(runId, 1)).thenReturn(List.of(new AgentArtifactRecord(), new AgentArtifactRecord()));
        when(events.findAfter(runId, 0, 200)).thenReturn(List.of(mock(AgentEvent.class)));

        DemoScenarioResult result = new DemoScenarioService(runs, publisher, events, artifacts)
                .run("same-scenario", DemoScenarioType.NORMAL_RESEARCH);

        assertTrue(result.reused());
        verify(runs, never()).createRootRun(anyString());
        verify(publisher, never()).publish(anyString(), any());
        verify(artifacts, never()).publish(anyString(), any(), any());
    }

    @Test
    void rejectsUnsafeScenarioIdsAndIncompleteExistingRuns() {
        AgentRunService runs = mock(AgentRunService.class);
        DemoScenarioService service = new DemoScenarioService(runs, mock(AgentEventPublisher.class),
                mock(AgentEventService.class), mock(AgentArticleArtifactService.class));
        assertThrows(IllegalArgumentException.class, () -> service.run("bad id", DemoScenarioType.NO_RESEARCH));
        String runId = "demo-" + shaPrefix("pending");
        when(runs.getByRunId(runId)).thenReturn(record(runId, "RUNNING"));
        assertThrows(IllegalStateException.class, () -> service.run("pending", DemoScenarioType.NO_RESEARCH));
    }

    private static AgentRunRecord record(String runId, String status) {
        AgentRunRecord record = new AgentRunRecord();
        record.setRunId(runId);
        record.setRootRunId(runId);
        record.setStatus(status);
        return record;
    }

    private static String shaPrefix(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 32);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
