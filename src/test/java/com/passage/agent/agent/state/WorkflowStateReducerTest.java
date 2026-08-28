package com.passage.agent.agent.state;

import com.passage.agent.agent.run.AgentRun;
import com.passage.agent.model.dto.article.ArticleState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowStateReducerTest {

    @Test
    void preservesInputsAndPreviouslyPublishedDataAcrossTypedTransitions() {
        WorkflowState initial = WorkflowState.start(
                AgentRun.root("run-1", Instant.parse("2026-08-27T08:00:00Z")),
                "article-task-1",
                new WorkflowState.ArticleInput("Agent 开发", "TECH", "面向初学者", List.of("PEXELS"))
        );
        ArticleState.TitleOption option = new ArticleState.TitleOption();
        option.setMainTitle("Agent 入门");
        option.setSubTitle("从状态开始");
        ArticleState.TitleResult title = new ArticleState.TitleResult();
        title.setMainTitle(option.getMainTitle());
        title.setSubTitle(option.getSubTitle());
        ArticleState.OutlineResult outline = new ArticleState.OutlineResult();
        outline.setSections(List.of());

        WorkflowState completedDraft = WorkflowStateReducer.withContent(
                WorkflowStateReducer.withOutline(
                        WorkflowStateReducer.withSelectedTitle(
                                WorkflowStateReducer.withTitleOptions(initial, List.of(option)), title),
                        outline),
                "正文"
        );

        assertThat(completedDraft.input().topic()).isEqualTo("Agent 开发");
        assertThat(completedDraft.draft().titleOptions()).containsExactly(option);
        assertThat(completedDraft.draft().selectedTitle()).isEqualTo(title);
        assertThat(completedDraft.draft().outline()).isEqualTo(outline);
        assertThat(completedDraft.draft().content()).isEqualTo("正文");
        assertThat(completedDraft.artifacts().images()).isEmpty();
    }
}
