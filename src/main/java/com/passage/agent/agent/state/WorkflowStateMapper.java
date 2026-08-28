package com.passage.agent.agent.state;

import com.passage.agent.agent.run.AgentRun;
import com.passage.agent.model.dto.article.ArticleState;

import java.util.Objects;

/** Compatibility boundary between the current HTTP/service DTO and the new typed workflow state. */
public final class WorkflowStateMapper {

    private WorkflowStateMapper() {
    }

    public static WorkflowState fromLegacy(AgentRun run, ArticleState legacy) {
        Objects.requireNonNull(legacy, "legacy");
        return new WorkflowState(
                run,
                legacy.getTaskId(),
                new WorkflowState.ArticleInput(
                        legacy.getTopic(), legacy.getStyle(), legacy.getUserDescription(), legacy.getEnabledImageMethods()),
                new WorkflowState.ArticleDraft(
                        legacy.getTitleOptions(), legacy.getTitle(), legacy.getOutline(), legacy.getContent()),
                new WorkflowState.ArticleArtifacts(
                        legacy.getImageRequirements(), legacy.getImages(), legacy.getCoverImage(), legacy.getFullContent())
        );
    }

    public static ArticleState toLegacy(WorkflowState state) {
        Objects.requireNonNull(state, "state");
        ArticleState legacy = new ArticleState();
        legacy.setTaskId(state.taskId());
        legacy.setTopic(state.input().topic());
        legacy.setStyle(state.input().style());
        legacy.setUserDescription(state.input().userDescription());
        legacy.setEnabledImageMethods(state.input().enabledImageMethods());
        legacy.setTitleOptions(state.draft().titleOptions());
        legacy.setTitle(state.draft().selectedTitle());
        legacy.setOutline(state.draft().outline());
        legacy.setContent(state.draft().content());
        legacy.setImageRequirements(state.artifacts().imageRequirements());
        legacy.setImages(state.artifacts().images());
        legacy.setCoverImage(state.artifacts().coverImage());
        legacy.setFullContent(state.artifacts().fullContent());
        return legacy;
    }
}
