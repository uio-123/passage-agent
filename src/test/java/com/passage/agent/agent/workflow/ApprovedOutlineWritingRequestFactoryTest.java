package com.passage.agent.agent.workflow;

import com.passage.agent.agent.fixture.ArticleWorkflowFixture;
import com.passage.agent.agent.state.WorkflowStateReducer;
import com.passage.agent.model.dto.article.ArticleState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ApprovedOutlineWritingRequestFactoryTest {
    private final ApprovedOutlineWritingRequestFactory factory = new ApprovedOutlineWritingRequestFactory();

    @Test
    void createsStableTasksAndAnExplicitNoResearchBundle() {
        var state = approvedState();
        ApprovedOutlineWritingRequest request = factory.create(state, 4L, null);

        assertThat(request.sectionTasks()).extracting(task -> task.id()).containsExactly("outline-section-0", "outline-section-1");
        assertThat(request.sectionTasks().getFirst().instruction()).contains("first point");
        assertThat(request.factEnhancementAvailable()).isFalse();
        assertThat(request.research().sources()).isEmpty();
        assertThat(request.research().unresolvedClaims()).containsExactly("Fact enhancement was not requested");
    }

    @Test
    void rejectsUnapprovedOutline() {
        assertThatIllegalArgumentException().isThrownBy(() -> factory.create(
                ArticleWorkflowFixture.workflowState("run", "task"), 0L, null));
    }

    private static com.passage.agent.agent.state.WorkflowState approvedState() {
        ArticleState.TitleResult title = new ArticleState.TitleResult();
        title.setMainTitle("Main");
        title.setSubTitle("Sub");
        ArticleState.OutlineSection first = new ArticleState.OutlineSection();
        first.setTitle("First"); first.setPoints(List.of("first point"));
        ArticleState.OutlineSection second = new ArticleState.OutlineSection();
        second.setTitle("Second"); second.setPoints(List.of());
        ArticleState.OutlineResult outline = new ArticleState.OutlineResult();
        outline.setSections(List.of(first, second));
        return WorkflowStateReducer.withOutline(WorkflowStateReducer.withSelectedTitle(
                ArticleWorkflowFixture.workflowState("run", "task"), title), outline);
    }
}
