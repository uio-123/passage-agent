package com.passage.agent.agent.state;

import com.passage.agent.agent.run.AgentRun;
import com.passage.agent.model.dto.article.ArticleState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowStateMapperTest {

    @Test
    void roundTripsCurrentArticleStateWithoutLosingWorkflowFields() {
        ArticleState legacy = new ArticleState();
        legacy.setTaskId("task-1");
        legacy.setTopic("Agent 开发");
        legacy.setStyle("TECH");
        legacy.setUserDescription("初学者");
        legacy.setEnabledImageMethods(List.of("PEXELS"));
        legacy.setTitleOptions(List.of(titleOption("标题", "副标题")));
        legacy.setTitle(title("标题", "副标题"));
        legacy.setOutline(outline());
        legacy.setContent("正文");
        legacy.setImageRequirements(List.of());
        legacy.setImages(List.of());
        legacy.setCoverImage("https://images.example/cover");
        legacy.setFullContent("完整正文");

        WorkflowState workflow = WorkflowStateMapper.fromLegacy(
                AgentRun.root("run-1", Instant.parse("2026-08-27T08:00:00Z")), legacy);
        ArticleState restored = WorkflowStateMapper.toLegacy(workflow);

        assertThat(restored.getTaskId()).isEqualTo(legacy.getTaskId());
        assertThat(restored.getTopic()).isEqualTo(legacy.getTopic());
        assertThat(restored.getEnabledImageMethods()).containsExactly("PEXELS");
        assertThat(restored.getTitle().getMainTitle()).isEqualTo("标题");
        assertThat(restored.getOutline().getSections()).hasSize(1);
        assertThat(restored.getContent()).isEqualTo("正文");
        assertThat(restored.getCoverImage()).isEqualTo("https://images.example/cover");
        assertThat(restored.getFullContent()).isEqualTo("完整正文");
    }

    private ArticleState.TitleOption titleOption(String mainTitle, String subTitle) {
        ArticleState.TitleOption option = new ArticleState.TitleOption();
        option.setMainTitle(mainTitle);
        option.setSubTitle(subTitle);
        return option;
    }

    private ArticleState.TitleResult title(String mainTitle, String subTitle) {
        ArticleState.TitleResult title = new ArticleState.TitleResult();
        title.setMainTitle(mainTitle);
        title.setSubTitle(subTitle);
        return title;
    }

    private ArticleState.OutlineResult outline() {
        ArticleState.OutlineSection section = new ArticleState.OutlineSection();
        section.setSection(1);
        section.setTitle("章节");
        section.setPoints(List.of("要点"));
        ArticleState.OutlineResult outline = new ArticleState.OutlineResult();
        outline.setSections(List.of(section));
        return outline;
    }
}
