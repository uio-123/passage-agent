package com.passage.agent.controller;

import com.passage.agent.agent.workflow.ContentQualityContinuationResult;
import com.passage.agent.agent.workflow.ContentQualityContinuationService;
import com.passage.agent.model.entity.User;
import com.passage.agent.service.ArticleService;
import com.passage.agent.service.UserService;
import com.passage.agent.service.AgentEventService;
import com.passage.agent.service.AgentRunService;
import com.passage.agent.service.AgentCheckpointService;
import com.passage.agent.service.AgentNodeExecutionService;
import com.passage.agent.service.AgentArticleArtifactService;
import com.passage.agent.service.AgentContextSnapshotService;
import com.passage.agent.manager.AgentEventSseManager;
import com.passage.agent.agent.event.AgentEvent;
import com.passage.agent.agent.event.AgentEventType;
import com.passage.agent.model.entity.AgentRunRecord;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentRunControllerTest {
    @Test
    void continuesAcceptedContentWithoutRequestBodyAfterArticleAuthorization() throws Exception {
        ContentQualityContinuationService continuation = mock(ContentQualityContinuationService.class);
        UserService users = mock(UserService.class);
        ArticleService articles = mock(ArticleService.class);
        User user = new User();
        when(users.getLoginUser(any())).thenReturn(user);
        when(continuation.continueDelivery("run-1"))
                .thenReturn(new ContentQualityContinuationResult("run-1", "ARTICLE_COMPLETED", "delivered"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AgentRunController(continuation, users, articles)).build();

        mvc.perform(post("/api/agent-runs/run-1/content-quality/continue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.runId").value("run-1"))
                .andExpect(jsonPath("$.data.stage").value("ARTICLE_COMPLETED"));

        verify(articles).getArticleDetail("run-1", user);
        verify(continuation).continueDelivery("run-1");
    }

    @Test
    void replaysOnlyEventsAfterRequestedSequenceFollowingArticleAuthorization() throws Exception {
        ContentQualityContinuationService continuation = mock(ContentQualityContinuationService.class);
        UserService users = mock(UserService.class); ArticleService articles = mock(ArticleService.class);
        AgentEventService events = mock(AgentEventService.class); AgentRunService runs = mock(AgentRunService.class);
        AgentCheckpointService checkpoints = mock(AgentCheckpointService.class); AgentNodeExecutionService nodes = mock(AgentNodeExecutionService.class);
        AgentArticleArtifactService artifacts = mock(AgentArticleArtifactService.class); AgentContextSnapshotService contexts = mock(AgentContextSnapshotService.class);
        User user = new User(); when(users.getLoginUser(any())).thenReturn(user);
        AgentRunRecord run = new AgentRunRecord(); run.setRunId("run-1"); run.setRootRunId("run-1"); run.setStatus("RUNNING"); run.setStateVersion(2L);
        when(runs.getByRunId("run-1")).thenReturn(run);
        when(events.findAfter("run-1", 3, 200)).thenReturn(List.of(new AgentEvent("run-1", 4, AgentEventType.NODE_COMPLETED,
                "writer", "writer", 1, java.util.Map.of("status", "completed"), Instant.now())));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AgentRunController(continuation, users, articles, events, runs,
                new AgentEventSseManager(), checkpoints, nodes, artifacts, contexts)).build();

        mvc.perform(get("/api/agent-runs/run-1/events").param("afterSequence", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.events[0].sequence").value(4))
                .andExpect(jsonPath("$.data.events[0].payload.status").value("completed"));
        verify(articles).getArticleDetail("run-1", user);
        verify(events).findAfter("run-1", 3, 200);
    }
}
