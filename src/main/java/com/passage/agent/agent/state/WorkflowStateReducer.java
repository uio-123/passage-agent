package com.passage.agent.agent.state;

import com.passage.agent.agent.run.AgentRun;
import com.passage.agent.model.dto.article.ArticleState;

import java.util.List;
import java.util.Objects;

/** Explicit state transitions used by future graph adapters and workflow nodes. */
public final class WorkflowStateReducer {

    private WorkflowStateReducer() {
    }

    public static WorkflowState withRun(WorkflowState state, AgentRun run) {
        return new WorkflowState(run, state.taskId(), state.input(), state.draft(), state.artifacts());
    }

    public static WorkflowState withTitleOptions(WorkflowState state, List<ArticleState.TitleOption> titleOptions) {
        return new WorkflowState(state.run(), state.taskId(), state.input(),
                new WorkflowState.ArticleDraft(titleOptions, state.draft().selectedTitle(), state.draft().outline(), state.draft().content()),
                state.artifacts());
    }

    public static WorkflowState withSelectedTitle(WorkflowState state, ArticleState.TitleResult selectedTitle) {
        return new WorkflowState(state.run(), state.taskId(), state.input(),
                new WorkflowState.ArticleDraft(state.draft().titleOptions(), Objects.requireNonNull(selectedTitle, "selectedTitle"),
                        state.draft().outline(), state.draft().content()),
                state.artifacts());
    }

    public static WorkflowState withOutline(WorkflowState state, ArticleState.OutlineResult outline) {
        return new WorkflowState(state.run(), state.taskId(), state.input(),
                new WorkflowState.ArticleDraft(state.draft().titleOptions(), state.draft().selectedTitle(),
                        Objects.requireNonNull(outline, "outline"), state.draft().content()),
                state.artifacts());
    }

    public static WorkflowState withContent(WorkflowState state, String content) {
        return new WorkflowState(state.run(), state.taskId(), state.input(),
                new WorkflowState.ArticleDraft(state.draft().titleOptions(), state.draft().selectedTitle(), state.draft().outline(),
                        Objects.requireNonNull(content, "content")),
                state.artifacts());
    }

    public static WorkflowState withImageArtifacts(
            WorkflowState state,
            List<ArticleState.ImageRequirement> imageRequirements,
            List<ArticleState.ImageResult> images,
            String coverImage,
            String fullContent
    ) {
        return new WorkflowState(state.run(), state.taskId(), state.input(), state.draft(),
                new WorkflowState.ArticleArtifacts(imageRequirements, images, coverImage, fullContent));
    }
}
