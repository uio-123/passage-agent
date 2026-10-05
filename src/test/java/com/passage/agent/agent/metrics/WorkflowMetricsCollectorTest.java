package com.passage.agent.agent.metrics;

import com.passage.agent.agent.ArticleAgentOrchestrator;
import com.passage.agent.agent.agents.ContentGeneratorAgent;
import com.passage.agent.agent.agents.ContentMergerAgent;
import com.passage.agent.agent.agents.ImageAnalyzerAgent;
import com.passage.agent.agent.agents.OutlineGeneratorAgent;
import com.passage.agent.agent.agents.TitleGeneratorAgent;
import com.passage.agent.agent.api.WorkflowErrorCode;
import com.passage.agent.agent.api.WorkflowExecutionResult;
import com.passage.agent.agent.api.WorkflowStage;
import com.passage.agent.agent.fixture.ArticleWorkflowFixture;
import com.passage.agent.agent.llm.MetricsCollectingAiModelPort;
import com.passage.agent.agent.parallel.ParallelImageGenerator;
import com.passage.agent.agent.state.WorkflowState;
import com.passage.agent.agent.state.WorkflowStateReducer;
import com.passage.agent.agent.workflow.ArticleWorkflowRunner;
import com.passage.agent.model.dto.article.ArticleState;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowMetricsCollectorTest {

    @Test
    void recordsComparableThreeStageBaselineFromFixedWorkflow() {
        WorkflowMetricsCollector collector = new WorkflowMetricsCollector();
        ArticleWorkflowRunner runner = new ArticleWorkflowRunner(
                orchestratorUsing(new MetricsCollectingAiModelPort(
                        new ArticleWorkflowFixture.DeterministicArticleModel(), collector)), collector);
        WorkflowState initial = ArticleWorkflowFixture.workflowState("metrics-run", "metrics-task");

        WorkflowState afterTitles = runner.generateTitles(initial, ignored -> { }).state();
        WorkflowState selected = WorkflowStateReducer.withSelectedTitle(afterTitles, title("Agent 开发入门", "从工作流到可控协作"));
        WorkflowState afterOutline = runner.generateOutline(selected, ignored -> { }).state();
        runner.generateContent(afterOutline, ignored -> { });

        WorkflowExecutionMetrics metrics = collector.find("metrics-run").orElseThrow();
        assertThat(metrics.stages()).extracting(stage -> stage.stage().name())
                .containsExactly("TITLES_GENERATED", "OUTLINE_GENERATED", "ARTICLE_COMPLETED");
        assertThat(metrics.stages()).allSatisfy(stage -> {
            assertThat(stage.duration().isNegative()).isFalse();
            assertThat(stage.modelCallCount()).isPositive();
            assertThat(stage.errorCode()).isNull();
        });
        assertThat(metrics.stages()).extracting(WorkflowStageMetrics::modelCallCount)
                .containsExactly(1, 1, 2);
        assertThat(metrics.resultStatus().name()).isEqualTo("COMPLETED");
        assertThat(metrics.errorCode()).isNull();
    }

    @Test
    void recordsProjectErrorCodeForRejectedStage() {
        WorkflowMetricsCollector collector = new WorkflowMetricsCollector();
        ArticleWorkflowRunner runner = new ArticleWorkflowRunner(new ArticleAgentOrchestrator(), collector);
        WorkflowState state = ArticleWorkflowFixture.workflowState("invalid-metrics-run", "metrics-task");

        assertThatThrownBy(() -> runner.generateOutline(state, ignored -> { }))
                .hasFieldOrPropertyWithValue("error.code", WorkflowErrorCode.INVALID_STATE);

        WorkflowExecutionMetrics metrics = collector.find("invalid-metrics-run").orElseThrow();
        assertThat(metrics.resultStatus().name()).isEqualTo("FAILED");
        assertThat(metrics.errorCode()).isEqualTo(WorkflowErrorCode.INVALID_STATE);
        assertThat(metrics.stages().getFirst().modelCallCount()).isZero();
    }

    @Test
    void capturesModelCallsFromWorkerThreadsCreatedInsideTheStage() {
        WorkflowMetricsCollector collector = new WorkflowMetricsCollector();
        WorkflowState state = ArticleWorkflowFixture.workflowState("parallel-metrics-run", "parallel-task");

        collector.measure("parallel-metrics-run", WorkflowStage.ARTICLE_COMPLETED, () -> {
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                CompletableFuture.runAsync(collector::recordModelCall, executor).join();
            }
            return new WorkflowExecutionResult(state, WorkflowStage.ARTICLE_COMPLETED);
        });

        assertThat(collector.find("parallel-metrics-run").orElseThrow().stages().getFirst().modelCallCount())
                .isEqualTo(1);
    }

    private ArticleState.TitleResult title(String mainTitle, String subTitle) {
        ArticleState.TitleResult title = new ArticleState.TitleResult();
        title.setMainTitle(mainTitle);
        title.setSubTitle(subTitle);
        return title;
    }

    private ArticleAgentOrchestrator orchestratorUsing(MetricsCollectingAiModelPort model) {
        ArticleAgentOrchestrator orchestrator = new ArticleAgentOrchestrator();
        ReflectionTestUtils.setField(orchestrator, "titleGeneratorAgent", new TitleGeneratorAgent(model));
        ReflectionTestUtils.setField(orchestrator, "outlineGeneratorAgent", new OutlineGeneratorAgent(model));
        ReflectionTestUtils.setField(orchestrator, "contentGeneratorAgent", new ContentGeneratorAgent(model));
        ReflectionTestUtils.setField(orchestrator, "imageAnalyzerAgent", new ImageAnalyzerAgent(model));
        ReflectionTestUtils.setField(orchestrator, "parallelImageGenerator", new ParallelImageGenerator(null));
        ReflectionTestUtils.setField(orchestrator, "contentMergerAgent", new ContentMergerAgent());
        return orchestrator;
    }
}
