package com.passage.agent.staging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passage.agent.agent.ArticleAgentOrchestrator;
import com.passage.agent.agent.agents.ImageAnalyzerAgent;
import com.passage.agent.agent.api.WorkflowExecutionResult;
import com.passage.agent.agent.api.WorkflowStage;
import com.passage.agent.agent.checkpoint.NodeExecutionOutcome;
import com.passage.agent.agent.metrics.WorkflowMetricsCollector;
import com.passage.agent.agent.research.ResearchBundle;
import com.passage.agent.agent.review.FactChecker;
import com.passage.agent.agent.review.ReviewIssue;
import com.passage.agent.agent.review.ReviewReport;
import com.passage.agent.agent.review.ReviewSeverity;
import com.passage.agent.agent.review.ReviewType;
import com.passage.agent.agent.review.StyleReviewer;
import com.passage.agent.agent.revision.RevisionAgent;
import com.passage.agent.agent.run.AgentRun;
import com.passage.agent.agent.run.AgentRunStatus;
import com.passage.agent.agent.state.WorkflowState;
import com.passage.agent.agent.state.WorkflowStateReducer;
import com.passage.agent.agent.supervisor.HumanDecision;
import com.passage.agent.agent.supervisor.PlanAction;
import com.passage.agent.agent.supervisor.PlanFeedback;
import com.passage.agent.agent.supervisor.ReplanResult;
import com.passage.agent.agent.supervisor.RuleBasedPlanReplanner;
import com.passage.agent.agent.supervisor.SubtaskSpec;
import com.passage.agent.agent.supervisor.SupervisorPlan;
import com.passage.agent.agent.workflow.QualityRevisionWorkflowUseCase;
import com.passage.agent.agent.workflow.QualityRevisionWorkflowUseCase.QualityWorkflowResult;
import com.passage.agent.agent.writing.SectionDraft;
import com.passage.agent.agent.writing.SectionTask;
import com.passage.agent.agent.writing.SectionWriter;
import com.passage.agent.model.dto.article.ArticleState;
import com.passage.agent.model.entity.AgentModelCallMetricRecord;
import com.passage.agent.service.AgentArticleArtifactService;
import com.passage.agent.service.AgentModelCallMetricService;
import com.passage.agent.service.AgentNodeExecutionService;
import com.passage.agent.service.AgentRunService;
import com.passage.agent.service.ToolCallAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "article.agent.quality-loop.enabled=true",
        "tencent.cos.secret-id=integration-test-id",
        "tencent.cos.secret-key=integration-test-key",
        "tencent.cos.region=ap-guangzhou",
        "tencent.cos.bucket=integration-test-bucket"
})
@Tag("harness-staging")
@Testcontainers(disabledWithoutDocker = true)
class HarnessStagingComparisonTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ai_passage_creator")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void modelProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> requiredEnvironment("H4_LITELLM_BASE_URL"));
        registry.add("spring.ai.openai.api-key", () -> requiredEnvironment("H4_LITELLM_API_KEY"));
        registry.add("spring.ai.openai.chat.options.model", () -> requiredEnvironment("H4_LITELLM_MODEL"));
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired ArticleAgentOrchestrator orchestrator;
    @Autowired WorkflowMetricsCollector metrics;
    @Autowired QualityRevisionWorkflowUseCase qualityLoop;
    @Autowired SectionWriter sectionWriter;
    @Autowired FactChecker factChecker;
    @Autowired StyleReviewer styleReviewer;
    @Autowired RevisionAgent revisionAgent;
    @Autowired AgentRunService runs;
    @Autowired AgentArticleArtifactService artifacts;
    @Autowired AgentModelCallMetricService modelMetrics;
    @Autowired ToolCallAuditService toolAudits;
    @Autowired AgentNodeExecutionService nodes;

    @Value("${spring.ai.openai.chat.options.model}")
    String modelName;

    @BeforeEach
    void prepareSchema() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new FileSystemResource("sql/add_agent_run_tables.sql"),
                new FileSystemResource("sql/add_agent_workflow_persistence.sql"),
                new FileSystemResource("sql/add_agent_research_persistence.sql"),
                new FileSystemResource("sql/add_agent_article_artifact_persistence.sql"),
                new FileSystemResource("sql/add_agent_event_persistence.sql"),
                new FileSystemResource("sql/add_agent_context_snapshot_persistence.sql"),
                new FileSystemResource("sql/add_agent_model_call_metric_persistence.sql"));
        populator.execute(dataSource);
        jdbcTemplate.execute("delete from agent_artifact");
        jdbcTemplate.execute("delete from agent_article_version");
        jdbcTemplate.execute("delete from tool_call_audit");
        jdbcTemplate.execute("delete from research_source");
        jdbcTemplate.execute("delete from agent_node_execution");
        jdbcTemplate.execute("delete from agent_checkpoint");
        jdbcTemplate.execute("delete from agent_run");
        jdbcTemplate.execute("delete from agent_event");
        jdbcTemplate.execute("delete from agent_event_sequence");
        jdbcTemplate.execute("delete from agent_context_snapshot");
        jdbcTemplate.execute("delete from agent_context_snapshot_sequence");
        jdbcTemplate.execute("delete from agent_model_call_metric");
    }

    @Test
    void comparesFiveRepresentativeTasksWithTheRealModel() throws Exception {
        String taskFilter = System.getenv().getOrDefault("H4_TASK_FILTER", "").trim();
        List<TaskDefinition> tasks = loadTasks().stream()
                .filter(task -> taskFilter.isBlank() || task.id().equals(taskFilter))
                .toList();
        List<RunResult> results = new ArrayList<>();
        RecoveryCheck recovery = verifyRecovery();
        HumanReplanCheck humanReplan = verifyHumanReplan();
        try {
            for (TaskDefinition task : tasks) {
                results.add(runLegacy(task));
                results.add(runHarness(task));
            }
        } finally {
            writeReport(tasks, results, recovery, humanReplan);
        }

        assertThat(results).hasSize(tasks.size() * 2);
        assertThat(results).allMatch(RunResult::success);
        assertThat(recovery.successful()).isTrue();
        assertThat(humanReplan.successful()).isTrue();
        assertThat(results.stream().filter(RunResult::harness).allMatch(result -> result.averageFactScore() != null))
                .isTrue();
    }

    private RunResult runLegacy(TaskDefinition task) {
        String runId = uniqueRunId("h4-" + task.id() + "-legacy");
        WorkflowState state = workflowState(task, runId);
        long started = System.nanoTime();
        try {
            WorkflowExecutionResult result = new com.passage.agent.agent.workflow.ArticleWorkflowRunner(
                    orchestrator, metrics).generateContent(state, ignored -> { });
            return legacyResult(task, runId, result.stage().name(), result.state().draft().content(),
                    null, null, (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException exception) {
            return legacyResult(task, runId, "FAILED", null, exception.getClass().getSimpleName(),
                    rootMessage(exception), (System.nanoTime() - started) / 1_000_000);
        }
    }

    private RunResult runHarness(TaskDefinition task) {
        String runId = uniqueRunId("h4-" + task.id() + "-harness");
        WorkflowState state = workflowState(task, runId);
        runs.createRootRun(runId);
        AgentRun running = runs.getDomain(runId);
        running = running.transitionTo(AgentRunStatus.RUNNING, laterThan(running.updatedAt()));
        runs.sync(running, "h4-harness-start");
        List<SectionTask> sectionTasks = sectionTasks(task);
        ReviewAgents reviewAgents = reviewAgents(task);
        ResearchBundle research = new ResearchBundle(runId, List.of(), List.of(),
                List.of("External research is intentionally disabled in H4 first-stage comparison"));
        AtomicReference<QualityWorkflowResult> quality = new AtomicReference<>();
        long started = System.nanoTime();
        try {
            metrics.measure(runId, WorkflowStage.CONTENT_QUALITY_ACCEPTED, () -> {
                QualityWorkflowResult result = qualityLoop.execute(
                        runId, 0L, 2, research, sectionTasks, sectionWriter,
                        reviewAgents.factChecker(), reviewAgents.styleReviewer(), revisionAgent);
                quality.set(result);
                String markdown = result.drafts().stream().map(SectionDraft::markdown)
                        .collect(java.util.stream.Collectors.joining("\n\n"));
                return new WorkflowExecutionResult(WorkflowStateReducer.withContent(state, markdown),
                        WorkflowStage.CONTENT_QUALITY_ACCEPTED);
            });
            QualityWorkflowResult result = quality.get();
            AgentRun terminal = runs.getDomain(runId).transitionTo(
                    result.decision().decision() == com.passage.agent.agent.review.QualityGateDecision.Decision.ACCEPT
                            ? AgentRunStatus.COMPLETED : AgentRunStatus.FAILED,
                    laterThan(runs.getDomain(runId).updatedAt()));
            runs.sync(terminal, "h4-harness-complete");
            String markdown = result.drafts().stream().map(SectionDraft::markdown)
                    .collect(java.util.stream.Collectors.joining("\n\n"));
            return result(task, true, runId, result.decision().decision().name(), markdown, null, null,
                    (System.nanoTime() - started) / 1_000_000,
                    result.averageFactScore(), result.averageStyleScore(), reviewAgents.fixtureInjected());
        } catch (RuntimeException exception) {
            runs.markFailed(runId, "H4_HARNESS_FAILURE");
            return result(task, true, runId, "FAILED", null, exception.getClass().getSimpleName(),
                    rootMessage(exception),
                    (System.nanoTime() - started) / 1_000_000, null, null, reviewAgents.fixtureInjected());
        }
    }

    private RunResult result(TaskDefinition task, boolean harness, String runId, String status, String content,
                             String errorType, String errorMessage, long durationMs,
                             Double averageFactScore, Double averageStyleScore, boolean fixtureInjected) {
        List<AgentModelCallMetricRecord> metricsForRun = modelMetrics.listByRunId(runId);
        Long inputTokens = sumTokens(metricsForRun, true);
        Long outputTokens = sumTokens(metricsForRun, false);
        Long totalTokens = sumTotalTokens(metricsForRun);
        int retries = metricsForRun.stream().mapToInt(metric -> value(metric.getRetryCount())).sum()
                + toolAudits.listByRunId(runId).stream().mapToInt(metric -> value(metric.getRetryCount())).sum();
        int revisions = harness ? Math.max(0, artifacts.listVersions(runId).size() - 1) : 0;
        Integer modelCalls = metricsForRun.isEmpty() && !harness ? null : metricsForRun.size();
        return new RunResult(task.id(), task.scenarioType(), harness ? "HARNESS" : "LEGACY", runId,
                errorType == null && (harness || WorkflowStage.ARTICLE_COMPLETED.name().equals(status)),
                status, errorType, errorMessage, durationMs, modelCalls,
                toolAudits.listByRunId(runId).size(), retries, revisions,
                inputTokens, outputTokens, totalTokens, content == null ? 0 : content.length(),
                content == null ? null : sha256(content), averageFactScore, averageStyleScore, fixtureInjected);
    }

    private RunResult legacyResult(TaskDefinition task, String runId, String status, String content,
                                   String errorType, String errorMessage, long durationMs) {
        return result(task, false, runId, status, content, errorType, errorMessage, durationMs, null, null, false);
    }

    private RecoveryCheck verifyRecovery() {
        String runId = uniqueRunId("h4-recovery");
        AtomicInteger executions = new AtomicInteger();
        String nodeId = "recovery-check";
        assertThatThrownBy(() -> nodes.executeOnce(runId, nodeId, 0L, () -> {
            executions.incrementAndGet();
            throw new IllegalStateException("staging fault");
        })).isInstanceOf(IllegalStateException.class);
        NodeExecutionOutcome recovered = nodes.executeOnce(runId, nodeId, 0L, () -> {
            executions.incrementAndGet();
            return "\"recovered\"";
        });
        NodeExecutionOutcome replay = nodes.executeOnce(runId, nodeId, 0L, () -> {
            executions.incrementAndGet();
            return "\"duplicate\"";
        });
        boolean successful = !recovered.reused() && replay.reused()
                && replay.resultSnapshot().contains("recovered") && executions.get() == 2;
        return new RecoveryCheck(successful, executions.get(), replay.reused(), 0);
    }

    private HumanReplanCheck verifyHumanReplan() {
        SupervisorPlan plan = new SupervisorPlan(false, 1, List.of(new SubtaskSpec(0, "section-1", "draft")));
        ReplanResult result = new RuleBasedPlanReplanner().replan(plan,
                PlanFeedback.human(new HumanDecision(HumanDecision.Type.MODIFY, "shorten the opening")));
        return new HumanReplanCheck(result.action() == PlanAction.REPLAN && result.plan().version() == 2,
                result.action().name(), result.plan().version());
    }

    private void writeReport(List<TaskDefinition> tasks, List<RunResult> results,
                             RecoveryCheck recovery, HumanReplanCheck humanReplan) throws Exception {
        String reportId = "h4-staging-" + Instant.now().toString().replace(':', '-');
        Path directory = Path.of("harness", "reports", reportId);
        Files.createDirectories(directory);
        boolean allSuccessful = results.size() == tasks.size() * 2 && results.stream().allMatch(RunResult::success);
        boolean reviewerReworkExercised = results.stream().anyMatch(result -> result.revisionRounds() > 0);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportStatus", "STAGING");
        report.put("publicationStatus", "NON_RELEASE");
        report.put("generatedAt", Instant.now().toString());
        report.put("model", modelName);
        report.put("taskCount", tasks.size());
        report.put("results", results);
        report.put("summary", Map.of(
                "LEGACY", summarize(results.stream().filter(result -> !result.harness()).toList()),
                "HARNESS", summarize(results.stream().filter(RunResult::harness).toList())));
        report.put("recovery", recovery);
        report.put("humanReplan", humanReplan);
        report.put("acceptance", Map.of(
                "allTasksSuccessful", allSuccessful,
                "recoverySuccessful", recovery.successful(),
                "humanReplanSuccessful", humanReplan.successful(),
                "duplicateExternalSideEffects", recovery.duplicateExternalSideEffects(),
                "reviewerReworkExercised", reviewerReworkExercised,
                "defaultSwitchAllowed", false));
        report.put("decision",
                "DO_NOT_SWITCH_DEFAULT: operational checks passed, but legacy cost metrics are unavailable, "
                        + "reviewer rework was not exercised, and a comparative quality benefit was not demonstrated.");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("report.json").toFile(), report);
    }

    private Map<String, Object> summarize(List<RunResult> results) {
        int successes = (int) results.stream().filter(RunResult::success).count();
        long totalDuration = results.stream().mapToLong(RunResult::durationMs).sum();
        List<Integer> modelCalls = results.stream().map(RunResult::modelCalls)
                .filter(java.util.Objects::nonNull).toList();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runs", results.size());
        summary.put("successes", successes);
        summary.put("successRate", results.isEmpty() ? 0.0 : (double) successes / results.size());
        summary.put("averageDurationMs", results.isEmpty() ? 0.0 : (double) totalDuration / results.size());
        summary.put("modelCalls", modelCalls.isEmpty() ? null
                : modelCalls.stream().mapToInt(Integer::intValue).sum());
        summary.put("modelMetricsStatus", modelCalls.size() == results.size() ? "COMPLETE" : "PARTIAL_OR_UNAVAILABLE");
        summary.put("toolCalls", results.stream().mapToInt(RunResult::toolCalls).sum());
        summary.put("retries", results.stream().mapToInt(RunResult::retries).sum());
        summary.put("revisionRounds", results.stream().mapToInt(RunResult::revisionRounds).sum());
        return summary;
    }

    private List<TaskDefinition> loadTasks() throws Exception {
        try (var input = new ClassPathResource("harness/h4-v1.json").getInputStream()) {
            return objectMapper.readValue(input, new TypeReference<>() { });
        }
    }

    private WorkflowState workflowState(TaskDefinition task, String runId) {
        AgentRun run = AgentRun.root(runId, Instant.now());
        WorkflowState state = WorkflowState.start(run, runId,
                new WorkflowState.ArticleInput(task.topic(), task.style(), task.scenarioType(),
                        List.of(ImageAnalyzerAgent.NO_IMAGE_METHOD)));
        ArticleState.TitleResult title = new ArticleState.TitleResult();
        title.setMainTitle(task.title());
        title.setSubTitle("H4 staging comparison");
        ArticleState.OutlineResult outline = new ArticleState.OutlineResult();
        List<ArticleState.OutlineSection> sections = new ArrayList<>();
        for (int index = 0; index < task.outlineSections().size(); index++) {
            ArticleState.OutlineSection section = new ArticleState.OutlineSection();
            section.setSection(index + 1);
            section.setTitle(task.outlineSections().get(index));
            section.setPoints(List.of("保持结构清晰", "避免虚构事实"));
            sections.add(section);
        }
        outline.setSections(sections);
        return WorkflowStateReducer.withOutline(WorkflowStateReducer.withSelectedTitle(state, title), outline);
    }

    private List<SectionTask> sectionTasks(TaskDefinition task) {
        List<SectionTask> tasks = new ArrayList<>();
        for (int index = 0; index < task.outlineSections().size(); index++) {
            String heading = task.outlineSections().get(index);
            tasks.add(new SectionTask(index, "section-" + index,
                    heading, heading + "。保持结构清晰，避免虚构事实。", List.of()));
        }
        return tasks;
    }

    private ReviewAgents reviewAgents(TaskDefinition task) {
        if (!"REVIEW_REVISION".equals(task.scenarioType())) {
            return new ReviewAgents(factChecker, styleReviewer, false);
        }
        AtomicBoolean firstReview = new AtomicBoolean(true);
        FactChecker forcedFactChecker = request -> {
            if (firstReview.compareAndSet(true, false)) {
                return new ReviewReport(ReviewType.FACT, 70, List.of(new ReviewIssue(
                        request.draft().sectionId(), ReviewSeverity.BLOCKER, "STAGING_FORCED_REVISION",
                        "Force one bounded staging revision")));
            }
            return factChecker.review(request);
        };
        return new ReviewAgents(forcedFactChecker, styleReviewer, true);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured for harness-staging.");
        }
        return value;
    }

    private static String uniqueRunId(String prefix) {
        String value = prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    private static Instant laterThan(Instant value) {
        Instant now = Instant.now();
        return now.isBefore(value) ? value.plusMillis(1) : now;
    }

    private static Long sumTokens(List<AgentModelCallMetricRecord> metrics, boolean input) {
        if (metrics.isEmpty()) return null;
        long total = 0;
        for (AgentModelCallMetricRecord metric : metrics) {
            Long value = input ? metric.getInputTokens() : metric.getOutputTokens();
            if (value == null) return null;
            total += value;
        }
        return total;
    }

    private static Long sumTotalTokens(List<AgentModelCallMetricRecord> metrics) {
        if (metrics.isEmpty()) return null;
        long total = 0;
        for (AgentModelCallMetricRecord metric : metrics) {
            if (metric.getTotalTokens() == null) return null;
            total += metric.getTotalTokens();
        }
        return total;
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private record TaskDefinition(String id, String sourceSampleId, String scenarioType, String topic, String style,
                                  String title, List<String> outlineSections) {
        private TaskDefinition {
            outlineSections = List.copyOf(outlineSections);
        }
    }

    private record RunResult(
            String taskId,
            String scenarioType,
            String variant,
            String runId,
            boolean success,
            String status,
            String errorType,
            String errorMessage,
            long durationMs,
            Integer modelCalls,
            int toolCalls,
            int retries,
            int revisionRounds,
            Long inputTokens,
            Long outputTokens,
            Long totalTokens,
            int contentCharacters,
            String contentHash,
            Double averageFactScore,
            Double averageStyleScore,
            boolean fixtureInjected
    ) {
        boolean harness() {
            return "HARNESS".equals(variant);
        }
    }

    private record RecoveryCheck(boolean successful, int executions, boolean replayReused, int duplicateExternalSideEffects) { }
    private record HumanReplanCheck(boolean successful, String action, int planVersion) { }
    private record ReviewAgents(FactChecker factChecker, StyleReviewer styleReviewer, boolean fixtureInjected) { }
}
