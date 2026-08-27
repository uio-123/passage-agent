package com.yupi.template.agent.state;

import com.yupi.template.agent.run.AgentRun;
import com.yupi.template.model.dto.article.ArticleState;

import java.util.List;
import java.util.Objects;

/**
 * Typed, framework-neutral state for one article workflow run. It is immutable
 * so graph nodes can only publish explicit state transitions through the reducer.
 */
public record WorkflowState(
        AgentRun run,
        String taskId,
        ArticleInput input,
        ArticleDraft draft,
        ArticleArtifacts artifacts
) {
    public WorkflowState {
        Objects.requireNonNull(run, "run");
        requireText(taskId, "taskId");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(artifacts, "artifacts");
    }

    public static WorkflowState start(AgentRun run, String taskId, ArticleInput input) {
        return new WorkflowState(run, taskId, input, ArticleDraft.empty(), ArticleArtifacts.empty());
    }

    public record ArticleInput(
            String topic,
            String style,
            String userDescription,
            List<String> enabledImageMethods
    ) {
        public ArticleInput {
            requireText(topic, "topic");
            enabledImageMethods = enabledImageMethods == null ? List.of() : List.copyOf(enabledImageMethods);
        }
    }

    public record ArticleDraft(
            List<ArticleState.TitleOption> titleOptions,
            ArticleState.TitleResult selectedTitle,
            ArticleState.OutlineResult outline,
            String content
    ) {
        public ArticleDraft {
            titleOptions = titleOptions == null ? List.of() : List.copyOf(titleOptions);
        }

        public static ArticleDraft empty() {
            return new ArticleDraft(List.of(), null, null, null);
        }
    }

    public record ArticleArtifacts(
            List<ArticleState.ImageRequirement> imageRequirements,
            List<ArticleState.ImageResult> images,
            String coverImage,
            String fullContent
    ) {
        public ArticleArtifacts {
            imageRequirements = imageRequirements == null ? List.of() : List.copyOf(imageRequirements);
            images = images == null ? List.of() : List.copyOf(images);
        }

        public static ArticleArtifacts empty() {
            return new ArticleArtifacts(List.of(), List.of(), null, null);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
