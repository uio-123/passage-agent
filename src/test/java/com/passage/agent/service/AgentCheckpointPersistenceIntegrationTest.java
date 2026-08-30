package com.passage.agent.service;

import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import com.passage.agent.agent.parallel.IdempotentImageGenerationGateway;
import com.passage.agent.agent.tools.ImageGenerationTool;
import com.passage.agent.agent.run.AgentRunStatus;
import com.passage.agent.agent.research.ResearchSource;
import com.passage.agent.agent.research.ResearchSourceStatus;
import com.passage.agent.agent.research.ResearchBundle;
import com.passage.agent.agent.research.GatewayResearchUseCase;
import com.passage.agent.agent.research.RegisteredSearchResult;
import com.passage.agent.agent.research.ResearchRequest;
import com.passage.agent.agent.policy.ToolCallAuditEvent;
import com.passage.agent.agent.policy.ToolPolicyError;
import com.passage.agent.agent.policy.ToolPolicy;
import com.passage.agent.agent.policy.ToolPolicyGateway;
import com.passage.agent.agent.tool.ToolAdapter;
import com.passage.agent.agent.tool.ToolCallRequest;
import com.passage.agent.agent.tool.ToolCallResult;
import com.passage.agent.agent.tool.ToolId;
import com.passage.agent.agent.tool.ToolRegistry;
import com.passage.agent.agent.writing.ParallelSectionWritingUseCase;
import com.passage.agent.agent.writing.SectionDraft;
import com.passage.agent.agent.writing.SectionTask;
import com.passage.agent.agent.artifact.ArticleArtifactManifestFactory;
import com.passage.agent.agent.artifact.ArticleVersionChain;
import com.passage.agent.agent.review.QualityGateDecision;
import com.passage.agent.agent.review.ReviewIssue;
import com.passage.agent.agent.review.ReviewSeverity;
import com.passage.agent.agent.review.ReviewReport;
import com.passage.agent.agent.review.ReviewType;
import com.passage.agent.agent.workflow.QualityRevisionWorkflowUseCase;
import com.passage.agent.model.entity.AgentRunRecord;
import com.passage.agent.agent.event.AgentEventInput;
import com.passage.agent.agent.event.AgentEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the database constraints and compare-and-set workflow rules against MySQL. */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=integration-test-key",
        "tencent.cos.secret-id=integration-test-id",
        "tencent.cos.secret-key=integration-test-key",
        "tencent.cos.region=ap-guangzhou",
        "tencent.cos.bucket=integration-test-bucket"
})
@Tag("persistence-integration")
@Testcontainers(disabledWithoutDocker = true)
class AgentCheckpointPersistenceIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ai_passage_creator")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AgentRunService agentRunService;

    @Autowired
    private AgentCheckpointService agentCheckpointService;

    @Autowired
    private AgentNodeExecutionService agentNodeExecutionService;

    @Autowired
    private WorkflowRecoveryService workflowRecoveryService;

    @Autowired
    private IdempotentImageGenerationGateway imageGateway;

    @Autowired
    private ResearchSourceService researchSourceService;

    @Autowired
    private ToolCallAuditService toolCallAuditService;

    @Autowired
    private ParallelSectionWritingUseCase parallelSectionWritingUseCase;

    @Autowired
    private AgentArticleArtifactService agentArticleArtifactService;

    @Autowired
    private QualityRevisionWorkflowUseCase qualityRevisionWorkflowUseCase;

    @Autowired
    private AgentEventService agentEventService;

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
    void persistsTraceableSourcesOnceAndStoresOnlyRedactedAuditData() {
        ResearchSource source = new ResearchSource("source-1", "https://example.com/page", "Example", "Publisher",
                Instant.parse("2026-08-29T00:00:00Z"), "hash-1", "Bounded summary", "query", ResearchSourceStatus.VERIFIED);
        var first = researchSourceService.saveOrReuse("run-research", source);
        var repeated = researchSourceService.saveOrReuse("run-research", source);
        researchSourceService.saveOrReuse("other-run", source);
        toolCallAuditService.record(new ToolCallAuditEvent("run-research", com.passage.agent.agent.tool.ToolId.WEB_READER,
                "https://example.com/hashed-path", false, ToolPolicyError.UNSAFE_URL, Duration.ofMillis(12), 0, 0));

        assertThat(repeated.getId()).isEqualTo(first.getId());
        assertThat(researchSourceService.listByRunId("run-research")).hasSize(1);
        assertThat(researchSourceService.listByRunId("other-run")).hasSize(1);
        assertThat(toolCallAuditService.listByRunId("run-research")).singleElement().satisfies(audit -> {
            assertThat(audit.getTarget()).doesNotContain("?");
            assertThat(audit.getErrorCode()).isEqualTo("UNSAFE_URL");
            assertThat(audit.getElapsedMillis()).isEqualTo(12L);
        });
    }

    @Test
    void persistsStrictlyIncreasingEventsAndNeverStoresSensitivePayload() {
        var first = agentEventService.append("event-run", new AgentEventInput(AgentEventType.NODE_STARTED, "writer", "writer", 1,
                java.util.Map.of("status", "running", "api-key", "must-not-persist")));
        var second = agentEventService.append("event-run", new AgentEventInput(AgentEventType.NODE_COMPLETED, "writer", "writer", 1,
                java.util.Map.of("status", "completed", "prompt", "must-not-persist")));

        assertThat(first.sequence()).isEqualTo(1);
        assertThat(second.sequence()).isEqualTo(2);
        assertThat(agentEventService.findAfter("event-run", 1, 10)).extracting(event -> event.sequence()).containsExactly(2L);
        String payload = jdbcTemplate.queryForObject("select payload from agent_event where runId = ? and sequence = 1", String.class, "event-run");
        assertThat(payload).contains("running").doesNotContain("must-not-persist").doesNotContain("api-key");
    }

    @Test
    void researchRetryReusesPersistedSourceAndDoesNotRepeatTheReader() {
        AtomicInteger readerCalls = new AtomicInteger();
        ToolAdapter reader = new ToolAdapter() {
            @Override public ToolId id() { return ToolId.WEB_READER; }
            @Override public ToolCallResult execute(ToolCallRequest request, ToolPolicy policy) {
                readerCalls.incrementAndGet();
                return new ToolCallResult(ToolId.WEB_READER, List.of(new ToolCallResult.SourceCandidate(
                        request.input(), "Evidence", "Example", "Persisted evidence", "retry-hash")), Duration.ZERO);
            }
        };
        GatewayResearchUseCase research = new GatewayResearchUseCase(
                new ToolPolicyGateway(new ToolRegistry(List.of(reader)), ToolPolicy.strictDefaults(), toolCallAuditService::record),
                researchSourceService);
        ResearchRequest request = new ResearchRequest("retry query", List.of(
                new RegisteredSearchResult("search-1", "https://example.com/evidence", "Evidence", "Example")),
                Set.of(ToolId.WEB_READER), 1);

        var first = research.execute("run-research-retry", request);
        var retried = research.execute("run-research-retry", request);

        assertThat(first.sources()).hasSize(1);
        assertThat(retried.sources()).hasSize(1);
        assertThat(readerCalls).hasValue(1);
        assertThat(researchSourceService.listByRunId("run-research-retry")).hasSize(1);
        assertThat(toolCallAuditService.listByRunId("run-research-retry")).hasSize(1);
    }

    @Test
    void sectionWriterRetryReusesTheDurableChildNodeSnapshot() {
        agentRunService.createRootRun("run-section-retry");
        ResearchBundle research = new ResearchBundle("research-section", List.of(new ResearchSource(
                "source-1", "https://example.com/evidence", "Evidence", "Example", Instant.EPOCH,
                "section-hash", "Evidence", "query", ResearchSourceStatus.VERIFIED)), List.of(), List.of("none"));
        SectionTask task = new SectionTask(0, "section-1", "Section", "Write the section", List.of("source-1"));
        AtomicInteger writerCalls = new AtomicInteger();

        var first = parallelSectionWritingUseCase.execute("run-section-retry", 0L, 1, research, List.of(task), request ->
                new SectionDraft(0, "section-1", "draft-" + writerCalls.incrementAndGet(), List.of("source-1")));
        var retried = parallelSectionWritingUseCase.execute("run-section-retry", 0L, 1, research, List.of(task), request ->
                new SectionDraft(0, "section-1", "duplicate-" + writerCalls.incrementAndGet(), List.of("source-1")));

        assertThat(first.drafts()).singleElement().extracting(SectionDraft::markdown).isEqualTo("draft-1");
        assertThat(retried.drafts()).singleElement().extracting(SectionDraft::markdown).isEqualTo("draft-1");
        assertThat(writerCalls).hasValue(1);
        String childRunId = first.childRunIds().get("section-1");
        assertThat(jdbcTemplate.queryForObject("select count(*) from agent_node_execution where runId = ?", Integer.class, childRunId))
                .isEqualTo(1);
    }

    @Test
    void persistsAppendOnlyArticleVersionsAndReusesAnIdenticalManifestOnRetry() {
        agentRunService.createRootRun("run-article-version");
        ResearchBundle research = new ResearchBundle("research-article", List.of(new ResearchSource(
                "source-1", "https://example.com/evidence", "Evidence", "Example", Instant.EPOCH,
                "article-hash", "Evidence", "query", ResearchSourceStatus.VERIFIED)), List.of("fact"), List.of());
        List<SectionDraft> drafts = List.of(new SectionDraft(0, "section-1", "original", List.of("source-1")));
        ArticleVersionChain initial = ArticleVersionChain.initial(drafts, "initial", Instant.EPOCH);
        QualityGateDecision accepted = new QualityGateDecision(QualityGateDecision.Decision.ACCEPT, 0, List.of());
        ArticleArtifactManifestFactory factory = new ArticleArtifactManifestFactory();
        var manifestOne = factory.create("run-article-version", initial.latest(), research, accepted, Instant.EPOCH);

        var first = agentArticleArtifactService.publish("run-article-version", initial.latest(), manifestOne);
        var retried = agentArticleArtifactService.publish("run-article-version", initial.latest(), manifestOne);
        ArticleVersionChain revised = initial.append(new com.passage.agent.agent.revision.RevisionResult(List.of(
                new SectionDraft(0, "section-1", "revised", List.of("source-1"))), List.of("section-1"), 1),
                "FACT: section-1", Instant.EPOCH.plusSeconds(1));
        QualityGateDecision revise = new QualityGateDecision(QualityGateDecision.Decision.REVISE, 1,
                List.of(new ReviewIssue("section-1", ReviewSeverity.MAJOR, "FACT", "Correct evidence")));
        agentArticleArtifactService.publish("run-article-version", revised.latest(),
                factory.create("run-article-version", revised.latest(), research, revise, Instant.EPOCH.plusSeconds(1)));

        assertThat(retried.getId()).isEqualTo(first.getId());
        assertThat(agentArticleArtifactService.listVersions("run-article-version"))
                .extracting(version -> version.getVersion()).containsExactly(1, 2);
        assertThat(agentArticleArtifactService.listVersions("run-article-version").get(1).getParentVersion()).isEqualTo(1);
        assertThat(agentArticleArtifactService.listArtifacts("run-article-version", 1)).hasSize(3)
                .allSatisfy(artifact -> assertThat(artifact.getSha256()).matches("[0-9a-f]{64}"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from agent_article_version where runId = ?", Integer.class,
                "run-article-version")).isEqualTo(2);
    }

    @Test
    void qualityLoopReusesWriterRevisionAndArtifactNodesAfterRetry() {
        agentRunService.createRootRun("run-quality-loop");
        ResearchBundle research = new ResearchBundle("research-quality", List.of(new ResearchSource(
                "source-1", "https://example.com/evidence", "Evidence", "Example", Instant.EPOCH,
                "quality-hash", "Evidence", "query", ResearchSourceStatus.VERIFIED)), List.of("fact"), List.of());
        List<SectionTask> tasks = List.of(new SectionTask(0, "section-1", "Section", "Write", List.of("source-1")));
        AtomicInteger writerCalls = new AtomicInteger();
        AtomicInteger revisionCalls = new AtomicInteger();
        var fact = (com.passage.agent.agent.review.FactChecker) request -> request.draft().markdown().contains("revised")
                ? new ReviewReport(ReviewType.FACT, 90, List.of())
                : new ReviewReport(ReviewType.FACT, 60, List.of(new ReviewIssue("section-1", ReviewSeverity.BLOCKER,
                "FACT", "Needs correction")));
        var style = (com.passage.agent.agent.review.StyleReviewer) draft -> new ReviewReport(ReviewType.STYLE, 90, List.of());

        var first = qualityRevisionWorkflowUseCase.execute("run-quality-loop", 0L, 1, research, tasks,
                request -> new SectionDraft(0, "section-1", "initial-" + writerCalls.incrementAndGet(), List.of("source-1")),
                fact, style, request -> new SectionDraft(0, "section-1", "revised-" + revisionCalls.incrementAndGet(), List.of("source-1")));
        var retried = qualityRevisionWorkflowUseCase.execute("run-quality-loop", 0L, 1, research, tasks,
                request -> new SectionDraft(0, "section-1", "duplicate-" + writerCalls.incrementAndGet(), List.of("source-1")),
                fact, style, request -> new SectionDraft(0, "section-1", "duplicate-" + revisionCalls.incrementAndGet(), List.of("source-1")));

        assertThat(first.decision().decision()).isEqualTo(QualityGateDecision.Decision.ACCEPT);
        assertThat(retried.decision().decision()).isEqualTo(QualityGateDecision.Decision.ACCEPT);
        assertThat(writerCalls).hasValue(1);
        assertThat(revisionCalls).hasValue(1);
        assertThat(agentArticleArtifactService.listVersions("run-quality-loop")).hasSize(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from agent_node_execution where runId = ?", Integer.class,
                "run-quality-loop")).isEqualTo(3);
    }

    @Test
    void writerFailureBlocksReviewAndPublicationButRetryReusesCompletedSections() {
        agentRunService.createRootRun("run-writer-failure");
        ResearchBundle research = qualityResearch();
        List<SectionTask> tasks = List.of(
                new SectionTask(0, "section-1", "One", "Write one", List.of("source-1")),
                new SectionTask(1, "section-2", "Two", "Write two", List.of("source-1")));
        AtomicInteger firstSectionCalls = new AtomicInteger();
        AtomicInteger secondSectionCalls = new AtomicInteger();
        var fact = (com.passage.agent.agent.review.FactChecker) request -> new ReviewReport(ReviewType.FACT, 90, List.of());
        var style = (com.passage.agent.agent.review.StyleReviewer) draft -> new ReviewReport(ReviewType.STYLE, 90, List.of());

        assertThatThrownBy(() -> qualityRevisionWorkflowUseCase.execute("run-writer-failure", 0L, 1, research, tasks,
                request -> {
                    if (request.task().id().equals("section-1")) {
                        return new SectionDraft(0, "section-1", "one-" + firstSectionCalls.incrementAndGet(), List.of("source-1"));
                    }
                    secondSectionCalls.incrementAndGet();
                    throw new IllegalStateException("writer unavailable");
                }, fact, style, request -> request.originalDraft())).isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(agentArticleArtifactService.listVersions("run-writer-failure")).isEmpty();

        var recovered = qualityRevisionWorkflowUseCase.execute("run-writer-failure", 0L, 1, research, tasks,
                request -> request.task().id().equals("section-1")
                        ? new SectionDraft(0, "section-1", "duplicate-" + firstSectionCalls.incrementAndGet(), List.of("source-1"))
                        : new SectionDraft(1, "section-2", "two-" + secondSectionCalls.incrementAndGet(), List.of("source-1")),
                fact, style, request -> request.originalDraft());

        assertThat(recovered.decision().decision()).isEqualTo(QualityGateDecision.Decision.ACCEPT);
        assertThat(firstSectionCalls).hasValue(1);
        assertThat(secondSectionCalls).hasValue(2);
        assertThat(agentArticleArtifactService.listVersions("run-writer-failure")).hasSize(1);
    }

    @Test
    void rejectsAfterTwoRevisionRoundsWithoutPublishingARejectedThirdVersion() {
        agentRunService.createRootRun("run-quality-reject");
        AtomicInteger revisions = new AtomicInteger();
        ReviewIssue issue = new ReviewIssue("section-1", ReviewSeverity.BLOCKER, "FACT", "Still unsupported");
        var rejected = qualityRevisionWorkflowUseCase.execute("run-quality-reject", 0L, 1, qualityResearch(),
                List.of(new SectionTask(0, "section-1", "One", "Write", List.of("source-1"))),
                request -> new SectionDraft(0, "section-1", "initial", List.of("source-1")),
                request -> new ReviewReport(ReviewType.FACT, 60, List.of(issue)),
                draft -> new ReviewReport(ReviewType.STYLE, 90, List.of()),
                request -> new SectionDraft(0, "section-1", "revision-" + revisions.incrementAndGet(), List.of("source-1")));

        assertThat(rejected.decision().decision()).isEqualTo(QualityGateDecision.Decision.REJECT_MAX_ROUNDS);
        assertThat(revisions).hasValue(2);
        assertThat(agentArticleArtifactService.listVersions("run-quality-reject")).hasSize(2);
        assertThat(agentArticleArtifactService.listArtifacts("run-quality-reject", 2)).hasSize(3);
    }

    private static ResearchBundle qualityResearch() {
        return new ResearchBundle("research-quality", List.of(new ResearchSource(
                "source-1", "https://example.com/evidence", "Evidence", "Example", Instant.EPOCH,
                "quality-hash", "Evidence", "query", ResearchSourceStatus.VERIFIED)), List.of("fact"), List.of());
    }

    @Test
    void persistsChildRunsAndAdvancesStateVersionWithTheCheckpoint() {
        agentRunService.createRootRun("run-root");
        AgentRunRecord child = agentRunService.createChildRun("run-child", "run-root");
        var root = agentRunService.getDomain("run-root");
        agentRunService.sync(root.transitionTo(AgentRunStatus.RUNNING, root.updatedAt().plusSeconds(1)), "start");

        WorkflowCheckpoint checkpoint = agentCheckpointService.persistCheckpoint(
                "run-root", 0L, "checkpoint-1", "titles", "{\"taskId\":\"run-root\"}",
                AgentRunStatus.WAITING_FOR_APPROVAL);

        assertThat(child.getRootRunId()).isEqualTo("run-root");
        assertThat(child.getParentRunId()).isEqualTo("run-root");
        assertThat(checkpoint.stateVersion()).isEqualTo(1L);
        assertThat(agentRunService.getByRunId("run-root").getStateVersion()).isEqualTo(1L);
    }

    @Test
    void allowsExactlyOneConcurrentResumerToClaimTheSameCheckpoint() throws Exception {
        agentRunService.createRootRun("run-claim");
        var root = agentRunService.getDomain("run-claim");
        agentRunService.sync(root.transitionTo(AgentRunStatus.RUNNING, root.updatedAt().plusSeconds(1)), "start");
        agentCheckpointService.persistCheckpoint(
                "run-claim", 0L, "checkpoint-claim", "titles", "{\"taskId\":\"run-claim\"}",
                AgentRunStatus.PAUSED);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        List<CompletableFuture<ClaimAttempt>> claims = List.of(
                claimAfterBarrier("checkpoint-claim", ready, release),
                claimAfterBarrier("checkpoint-claim", ready, release));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        release.countDown();

        List<ClaimAttempt> attempts = claims.stream().map(CompletableFuture::join).toList();
        long successfulClaims = attempts.stream().filter(ClaimAttempt::claimed).count();
        assertThat(successfulClaims).isEqualTo(1L);
        assertThat(attempts).filteredOn(attempt -> !attempt.claimed()).hasSize(1);
        assertThat(agentRunService.getDomain("run-claim").status()).isEqualTo(AgentRunStatus.RUNNING);
    }

    @Test
    void reusesACompletedNodeResultWithoutRepeatingTheAction() {
        AtomicInteger sideEffects = new AtomicInteger();

        var first = agentNodeExecutionService.executeOnce("run-node", "image", 2L, () -> {
            sideEffects.incrementAndGet();
            return "{\"image\":\"first\"}";
        });
        var repeated = agentNodeExecutionService.executeOnce("run-node", "image", 2L, () -> {
            sideEffects.incrementAndGet();
            return "{\"image\":\"duplicate\"}";
        });

        assertThat(first.reused()).isFalse();
        assertThat(repeated.reused()).isTrue();
        assertThat(repeated.resultSnapshot()).contains("\"first\"");
        assertThat(sideEffects).hasValue(1);
    }

    @Test
    void rejectsConcurrentReplayButAllowsAnExplicitRetryAfterFailure() throws Exception {
        CountDownLatch actionStarted = new CountDownLatch(1);
        CountDownLatch releaseAction = new CountDownLatch(1);
        AtomicInteger sideEffects = new AtomicInteger();
        CompletableFuture<String> first = CompletableFuture.supplyAsync(() ->
                agentNodeExecutionService.executeOnce("run-concurrent", "upload", 1L, () -> {
                    sideEffects.incrementAndGet();
                    actionStarted.countDown();
                    try {
                        releaseAction.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("interrupted", exception);
                    }
                    return "{\"upload\":\"once\"}";
                }).resultSnapshot());
        assertThat(actionStarted.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                agentNodeExecutionService.executeOnce("run-concurrent", "upload", 1L, () -> "duplicate")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already running");
        releaseAction.countDown();
        assertThat(first.join()).isEqualTo("{\"upload\":\"once\"}");
        assertThat(sideEffects).hasValue(1);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                agentNodeExecutionService.executeOnce("run-retry", "upload", 1L, () -> {
                    throw new IllegalStateException("temporary failure");
                }))).isInstanceOf(IllegalStateException.class);
        var retried = agentNodeExecutionService.executeOnce("run-retry", "upload", 1L, () -> "{\"upload\":\"retried\"}");
        assertThat(retried.reused()).isFalse();
        assertThat(retried.resultSnapshot()).isEqualTo("{\"upload\":\"retried\"}");
    }

    @Test
    void cancellationWinsOverAReadyCheckpointAndPreventsLaterResume() {
        agentRunService.createRootRun("run-cancel");
        var root = agentRunService.getDomain("run-cancel");
        agentRunService.sync(root.transitionTo(AgentRunStatus.RUNNING, root.updatedAt().plusSeconds(1)), "start");
        agentCheckpointService.persistCheckpoint("run-cancel", 0L, "checkpoint-cancel", "outline", "{}",
                AgentRunStatus.PAUSED);

        assertThat(workflowRecoveryService.cancel("run-cancel")).isTrue();
        assertThat(agentRunService.getDomain("run-cancel").status()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                agentCheckpointService.claimForResume("checkpoint-cancel")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reusesAnImageSideEffectWhenCheckpointPublicationIsRetried() {
        agentRunService.createRootRun("run-image-retry");
        AtomicInteger sideEffects = new AtomicInteger();

        var first = imageGateway.execute("run-image-retry", "image:cover", () -> imageResult(
                sideEffects.incrementAndGet(), "https://images.example/first"));
        // Simulate a failed checkpoint write: stateVersion stays unchanged, so
        // retrying the same graph node must read the committed side effect.
        var retried = imageGateway.execute("run-image-retry", "image:cover", () -> imageResult(
                sideEffects.incrementAndGet(), "https://images.example/duplicate"));

        assertThat(first.getUrl()).isEqualTo("https://images.example/first");
        assertThat(retried.getUrl()).isEqualTo("https://images.example/first");
        assertThat(sideEffects).hasValue(1);
    }

    private ImageGenerationTool.ImageGenerationResult imageResult(int ignored, String url) {
        ImageGenerationTool.ImageGenerationResult result = new ImageGenerationTool.ImageGenerationResult();
        result.setSuccess(true);
        result.setUrl(url);
        return result;
    }

    private CompletableFuture<ClaimAttempt> claimAfterBarrier(
            String checkpointId, CountDownLatch ready, CountDownLatch release) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    return new ClaimAttempt(false, "barrier timeout");
                }
                agentCheckpointService.claimForResume(checkpointId);
                return new ClaimAttempt(true, null);
            } catch (RuntimeException exception) {
                return new ClaimAttempt(false, exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return new ClaimAttempt(false, "interrupted");
            }
        });
    }

    private record ClaimAttempt(boolean claimed, String failureMessage) {
    }
}
