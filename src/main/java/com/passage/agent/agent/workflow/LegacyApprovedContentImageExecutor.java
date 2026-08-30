package com.passage.agent.agent.workflow;

import com.passage.agent.agent.ArticleAgentOrchestrator;
import com.passage.agent.model.dto.article.ArticleState;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/** Bridges accepted content to legacy image agents without invoking ContentGenerator. */
@Service
public class LegacyApprovedContentImageExecutor implements ApprovedContentImageExecutor {
    private final ArticleAgentOrchestrator orchestrator;

    public LegacyApprovedContentImageExecutor(ArticleAgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public ApprovedContentImageResult execute(ApprovedContentImageRequest request, Consumer<String> streamHandler) {
        ArticleState state = new ArticleState();
        state.setTaskId(request.runId());
        state.setStyle(request.style());
        state.setEnabledImageMethods(request.enabledImageMethods());
        state.setContent(request.acceptedMarkdown());
        ArticleState.TitleResult title = new ArticleState.TitleResult();
        title.setMainTitle(request.mainTitle());
        title.setSubTitle(request.subTitle());
        state.setTitle(title);
        orchestrator.executeAcceptedContentImages(state, streamHandler);
        return new ApprovedContentImageResult(state.getFullContent(), state.getImageRequirements(), state.getImages());
    }
}
