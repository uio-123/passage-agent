package com.passage.agent.agent.workflow;

import com.passage.agent.agent.ArticleAgentOrchestrator;
import com.passage.agent.model.dto.article.ArticleState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LegacyApprovedContentImageExecutorTest {

    @Test
    void mapsOnlyAcceptedContentInputsToTheImageOnlyOrchestratorMethod() {
        ArticleAgentOrchestrator orchestrator = mock(ArticleAgentOrchestrator.class);
        doAnswer(invocation -> {
            ArticleState state = invocation.getArgument(0);
            state.setFullContent("accepted markdown with image");
            state.setImageRequirements(List.of());
            state.setImages(List.of());
            return null;
        }).when(orchestrator).executeAcceptedContentImages(any(), any());
        LegacyApprovedContentImageExecutor executor = new LegacyApprovedContentImageExecutor(orchestrator);

        ApprovedContentImageResult result = executor.execute(new ApprovedContentImageRequest(
                "run-1", "Main title", "Sub title", "guide", List.of("PEXELS"), "accepted markdown"), ignored -> { });

        assertThat(result.fullContent()).isEqualTo("accepted markdown with image");
        verify(orchestrator).executeAcceptedContentImages(any(), any());
    }
}
