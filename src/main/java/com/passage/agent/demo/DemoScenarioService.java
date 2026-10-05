package com.passage.agent.demo;

import com.passage.agent.agent.artifact.ArtifactManifest;
import com.passage.agent.agent.artifact.ArtifactType;
import com.passage.agent.agent.artifact.ArticleVersion;
import com.passage.agent.agent.event.AgentEventInput;
import com.passage.agent.agent.event.AgentEventPublisher;
import com.passage.agent.agent.event.AgentEventType;
import com.passage.agent.agent.run.AgentRun;
import com.passage.agent.agent.run.AgentRunStatus;
import com.passage.agent.agent.writing.SectionDraft;
import com.passage.agent.model.entity.AgentRunRecord;
import com.passage.agent.service.AgentArticleArtifactService;
import com.passage.agent.service.AgentEventService;
import com.passage.agent.service.AgentRunService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Persists a deterministic, no-model scenario through the production Run/Event/Artifact boundaries.
 * It is intentionally available only in the explicitly acknowledged demo profile.
 */
@Service
@Profile("demo")
public class DemoScenarioService {
    private final AgentRunService runs;
    private final AgentEventPublisher eventPublisher;
    private final AgentEventService events;
    private final AgentArticleArtifactService artifacts;

    public DemoScenarioService(AgentRunService runs, AgentEventPublisher eventPublisher,
                               AgentEventService events, AgentArticleArtifactService artifacts) {
        this.runs = runs;
        this.eventPublisher = eventPublisher;
        this.events = events;
        this.artifacts = artifacts;
    }

    @Transactional
    public DemoScenarioResult run(String scenarioId, DemoScenarioType scenarioType) {
        requireScenarioId(scenarioId);
        if (scenarioType == null) throw new IllegalArgumentException("scenarioType must not be null");
        String runId = "demo-" + sha256(scenarioId).substring(0, 32);
        AgentRunRecord existing = runs.getByRunId(runId);
        boolean reused = existing != null;

        if (existing == null) {
            runs.createRootRun(runId);
            AgentRun running = transitionNow(runs.getDomain(runId), AgentRunStatus.RUNNING);
            runs.sync(running, "demo-start");
            publishScenarioEvents(runId, scenarioType);
            publishArtifacts(runId, scenarioType);
            AgentRun completed = transitionNow(runs.getDomain(runId), AgentRunStatus.COMPLETED);
            runs.sync(completed, "demo-complete");
        } else if (!AgentRunStatus.COMPLETED.name().equals(existing.getStatus())) {
            throw new IllegalStateException("Existing demo scenario did not reach a terminal success state");
        }

        AgentRunRecord stored = runs.getByRunId(runId);
        int versionCount = artifacts.listVersions(runId).size();
        int artifactCount = versionCount == 0 ? 0 : artifacts.listArtifacts(runId, 1).size();
        int eventCount = events.findAfter(runId, 0, 200).size();
        boolean recovered = scenarioType != DemoScenarioType.RECOVERABLE_FAULT || eventCount > 0;
        return new DemoScenarioResult(scenarioId, scenarioType, runId, stored.getStatus(), versionCount,
                artifactCount, eventCount, recovered, scenarioType == DemoScenarioType.RECOVERABLE_FAULT ? 1 : 0,
                1, 0, reused);
    }

    private void publishScenarioEvents(String runId, DemoScenarioType scenarioType) {
        publish(runId, AgentEventType.NODE_COMPLETED, "title-generator", Map.of("stage", "TITLE_SELECTED"));
        publish(runId, AgentEventType.NODE_COMPLETED, "outline-generator", Map.of("stage", "OUTLINE_APPROVED"));
        publish(runId, AgentEventType.NODE_COMPLETED, "section-writer", Map.of("stage", "CONTENT_DRAFTED"));
        if (scenarioType == DemoScenarioType.REVIEW_REVISION) {
            publish(runId, AgentEventType.REVIEW_COMPLETED, "quality-review", Map.of("decision", "REVISE"));
            publish(runId, AgentEventType.NODE_COMPLETED, "section-revision", Map.of("stage", "CONTENT_REVISED"));
        } else {
            publish(runId, AgentEventType.REVIEW_COMPLETED, "quality-review", Map.of("decision", "ACCEPT"));
        }
        if (scenarioType == DemoScenarioType.RECOVERABLE_FAULT) {
            publish(runId, AgentEventType.NODE_RETRYING, "artifact-publisher", Map.of("errorCode", "DEMO_RECOVERABLE_FAULT"));
            publish(runId, AgentEventType.CHECKPOINT_READY, "artifact-publisher", Map.of("status", "RECOVERED"));
        }
    }

    private void publishArtifacts(String runId, DemoScenarioType scenarioType) {
        Instant now = Instant.now();
        String markdown = "# Passage Agent Demo\n\nDeterministic fixture for " + scenarioType.name() + ".";
        ArticleVersion version = new ArticleVersion(1, null, "DEMO_FIXED_FIXTURE",
                List.of(new SectionDraft(0, "demo-section", markdown, List.of())), now);
        String prefix = "run://" + runId + "/article/v1/";
        ArtifactManifest manifest = new ArtifactManifest(runId, List.of(
                new ArtifactManifest.Artifact("article-markdown-v1", ArtifactType.ARTICLE_MARKDOWN,
                        prefix + "article.md", sha256(markdown)),
                new ArtifactManifest.Artifact("source-bundle-v1", ArtifactType.SOURCE_BUNDLE,
                        prefix + "sources.json", sha256("DEMO_SYNTHETIC_SOURCE")),
                new ArtifactManifest.Artifact("quality-report-v1", ArtifactType.QUALITY_REPORT,
                        prefix + "quality.json", sha256("DRAFT|NON_RELEASE|" + scenarioType.name()))
        ), now);
        artifacts.publish(runId, version, manifest);
    }

    private void publish(String runId, AgentEventType type, String nodeId, Map<String, String> payload) {
        eventPublisher.publish(runId, new AgentEventInput(type, nodeId, "demo-fixture", 1, payload));
    }

    private static void requireScenarioId(String scenarioId) {
        if (scenarioId == null || scenarioId.isBlank() || scenarioId.length() > 256
                || !scenarioId.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("scenarioId must be 1..256 safe identifier characters");
        }
    }

    private static AgentRun transitionNow(AgentRun run, AgentRunStatus target) {
        Instant now = Instant.now();
        return run.transitionTo(target, now.isBefore(run.updatedAt()) ? run.updatedAt() : now);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
