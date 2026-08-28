package com.passage.agent.agent.workflow;

import com.passage.agent.agent.ArticleAgentOrchestrator;
import com.passage.agent.agent.api.WorkflowErrorCode;
import com.passage.agent.agent.api.WorkflowExecutionException;
import com.passage.agent.agent.api.WorkflowExecutionResult;
import com.passage.agent.agent.api.WorkflowStage;
import com.passage.agent.agent.fixture.ArticleWorkflowFixture;
import com.passage.agent.agent.graph.ArticleWorkflowGraphAdapter;
import com.passage.agent.agent.metrics.WorkflowMetricsCollector;
import com.passage.agent.agent.run.AgentRunStatus;
import com.passage.agent.agent.state.WorkflowState;
import com.passage.agent.agent.state.WorkflowStateReducer;
import com.passage.agent.model.dto.article.ArticleState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticleWorkflowRunnerTest {

    @Test
    void runsPhasesThroughTypedStateAndPreservesApprovalBoundaries() {
        ArticleWorkflowRunner runner = new ArticleWorkflowRunner(
                new ArticleWorkflowGraphAdapter(new DeterministicOrchestrator()), new WorkflowMetricsCollector());
        WorkflowState initial = ArticleWorkflowFixture.workflowState("run-1", "task-1");

        WorkflowExecutionResult titlesResult = runner.generateTitles(initial, ignored -> { });
        WorkflowState afterTitles = titlesResult.state();
        ArticleState.TitleResult selectedTitle = new ArticleState.TitleResult();
        selectedTitle.setMainTitle("标题");
        selectedTitle.setSubTitle("副标题");
        WorkflowState afterSelection = WorkflowStateReducer.withSelectedTitle(afterTitles, selectedTitle);
        WorkflowState afterOutline = runner.generateOutline(afterSelection, ignored -> { }).state();
        WorkflowState completed = runner.generateContent(afterOutline, ignored -> { }).state();

        assertThat(afterTitles.run().status()).isEqualTo(AgentRunStatus.WAITING_FOR_APPROVAL);
        assertThat(titlesResult.stage()).isEqualTo(WorkflowStage.TITLES_GENERATED);
        assertThat(afterOutline.draft().outline().getSections()).hasSize(1);
        assertThat(completed.run().status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(completed.draft().content()).isEqualTo("正文");
        assertThat(completed.artifacts().fullContent()).isEqualTo("完整正文");
    }

    @Test
    void rejectsOutlineGenerationBeforeAUserSelectsATitle() {
        ArticleWorkflowRunner runner = new ArticleWorkflowRunner(new DeterministicOrchestrator());
        WorkflowState initial = ArticleWorkflowFixture.workflowState("run-2", "task-2");

        assertThatThrownBy(() -> runner.generateOutline(initial, ignored -> { }))
                .isInstanceOf(WorkflowExecutionException.class)
                .extracting(exception -> ((WorkflowExecutionException) exception).error())
                .satisfies(error -> {
                    assertThat(error.code()).isEqualTo(WorkflowErrorCode.INVALID_STATE);
                    assertThat(error.stage()).isEqualTo(WorkflowStage.OUTLINE_GENERATED);
                    assertThat(error.retryable()).isFalse();
                });
    }

    @Test
    void classifiesUnifiedGraphNodeFailureAsRetryableGraphExecution() {
        ArticleWorkflowRunner runner = new ArticleWorkflowRunner(
                new ArticleWorkflowGraphAdapter(new FailingTitleOrchestrator()), new WorkflowMetricsCollector());

        assertThatThrownBy(() -> runner.generateTitles(ArticleWorkflowFixture.workflowState("run-3", "task-3"), ignored -> { }))
                .isInstanceOf(WorkflowExecutionException.class)
                .extracting(exception -> ((WorkflowExecutionException) exception).error())
                .satisfies(error -> {
                    assertThat(error.code()).isEqualTo(WorkflowErrorCode.GRAPH_EXECUTION);
                    assertThat(error.stage()).isEqualTo(WorkflowStage.TITLES_GENERATED);
                    assertThat(error.retryable()).isTrue();
                });
    }

    private static final class DeterministicOrchestrator extends ArticleAgentOrchestrator {
        @Override
        public void executePhase1_GenerateTitles(ArticleState state, java.util.function.Consumer<String> streamHandler) {
            ArticleState.TitleOption option = new ArticleState.TitleOption();
            option.setMainTitle("标题");
            option.setSubTitle("副标题");
            state.setTitleOptions(List.of(option));
        }

        @Override
        public void executePhase2_GenerateOutline(ArticleState state, java.util.function.Consumer<String> streamHandler) {
            ArticleState.OutlineSection section = new ArticleState.OutlineSection();
            section.setSection(1);
            section.setTitle("章节");
            section.setPoints(List.of("要点"));
            ArticleState.OutlineResult outline = new ArticleState.OutlineResult();
            outline.setSections(List.of(section));
            state.setOutline(outline);
        }

        @Override
        public void executePhase3_GenerateContent(ArticleState state, java.util.function.Consumer<String> streamHandler) {
            state.setContent("正文");
            state.setFullContent("完整正文");
            state.setImageRequirements(List.of());
            state.setImages(List.of());
        }
    }

    private static final class FailingTitleOrchestrator extends ArticleAgentOrchestrator {
        @Override
        public void executePhase1_GenerateTitles(ArticleState state, java.util.function.Consumer<String> streamHandler) {
            throw new IllegalStateException("simulated graph node failure");
        }
    }
}
