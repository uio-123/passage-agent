package com.passage.agent.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passage.agent.agent.checkpoint.CheckpointStatus;
import com.passage.agent.agent.checkpoint.NodeExecutionOutcome;
import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import com.passage.agent.agent.run.AgentRun;
import com.passage.agent.agent.run.AgentRunStatus;
import com.passage.agent.model.entity.AgentArticleVersionRecord;
import com.passage.agent.service.*;
import com.passage.agent.manager.SseEmitterManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContentQualityContinuationServiceTest {
    @Test
    void reusesPersistedImageDeliveryThenPublishesArtifactsBackfillsArticleAndCompletesRun() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        P3ContentCheckpointCodec codec = new P3ContentCheckpointCodec(mapper);
        String payload = codec.write(new P3ContentCheckpointSnapshot("run-1", P3ContentCheckpointSnapshot.MODE, 4,
                "accepted", 1, List.of("article-markdown-v1"), "ACCEPT",
                new P3ContentDeliveryContext("title", null, "guide", List.of("PEXELS"))));
        WorkflowCheckpoint checkpoint = new WorkflowCheckpoint("cp", "run-1", "content-quality-accepted", 4, payload,
                CheckpointStatus.READY, Instant.EPOCH, Instant.EPOCH);
        AgentCheckpointService checkpoints = mock(AgentCheckpointService.class);
        WorkflowRecoveryService recovery = mock(WorkflowRecoveryService.class);
        ApprovedContentImageExecutor imageExecutor = mock(ApprovedContentImageExecutor.class);
        AgentNodeExecutionService nodes = mock(AgentNodeExecutionService.class);
        AgentArticleArtifactService artifacts = mock(AgentArticleArtifactService.class);
        AgentRunService runs = mock(AgentRunService.class);
        ArticleService articles = mock(ArticleService.class);
        SseEmitterManager sse = mock(SseEmitterManager.class);
        when(checkpoints.findReadyCheckpoint("run-1", "content-quality-accepted")).thenReturn(checkpoint);
        doAnswer(invocation -> { ((Consumer<WorkflowCheckpoint>) invocation.getArgument(1)).accept(checkpoint); return null; })
                .when(recovery).resume(eq("cp"), any());
        when(nodes.executeOnce(eq("run-1"), eq("accepted-content-image-delivery"), eq(4L), any()))
                .thenReturn(new NodeExecutionOutcome("run-1:accepted-content-image-delivery:4", true,
                        "{\"fullContent\":\"delivered\",\"imageRequirements\":[],\"images\":[]}"));
        AgentArticleVersionRecord parent = new AgentArticleVersionRecord(); parent.setVersion(1);
        when(artifacts.listVersions("run-1")).thenReturn(List.of(parent));
        AgentRun running = AgentRun.root("run-1", Instant.EPOCH).transitionTo(AgentRunStatus.RUNNING, Instant.EPOCH);
        when(runs.getDomain("run-1")).thenReturn(running);
        ContentQualityContinuationService service = new ContentQualityContinuationService(checkpoints, recovery, codec,
                imageExecutor, nodes, artifacts, runs, articles, sse, mapper);

        ContentQualityContinuationResult result = service.continueDelivery("run-1");

        assertThat(result.fullContent()).isEqualTo("delivered");
        verify(imageExecutor, never()).execute(any(), any());
        verify(artifacts).publish(eq("run-1"), any(), any());
        verify(articles).saveArticleContent(eq("run-1"), any());
        verify(runs).sync(any(), eq("article-completed"));
        verify(sse).complete("run-1");
    }
}
