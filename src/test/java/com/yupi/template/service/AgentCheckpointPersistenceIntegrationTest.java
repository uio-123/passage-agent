package com.yupi.template.service;

import com.yupi.template.agent.checkpoint.WorkflowCheckpoint;
import com.yupi.template.agent.parallel.IdempotentImageGenerationGateway;
import com.yupi.template.agent.tools.ImageGenerationTool;
import com.yupi.template.agent.run.AgentRunStatus;
import com.yupi.template.model.entity.AgentRunRecord;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

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

    @BeforeEach
    void prepareSchema() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new FileSystemResource("sql/add_agent_run_tables.sql"),
                new FileSystemResource("sql/add_agent_workflow_persistence.sql"));
        populator.execute(dataSource);
        jdbcTemplate.execute("delete from agent_node_execution");
        jdbcTemplate.execute("delete from agent_checkpoint");
        jdbcTemplate.execute("delete from agent_run");
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
