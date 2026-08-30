package com.passage.agent.agent.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passage.agent.agent.artifact.ArtifactManifest;
import com.passage.agent.agent.artifact.ArtifactType;
import com.passage.agent.agent.checkpoint.NodeExecutionOutcome;
import com.passage.agent.agent.artifact.ArticleVersion;
import com.passage.agent.agent.run.AgentRun;
import com.passage.agent.agent.run.AgentRunStatus;
import com.passage.agent.agent.writing.SectionDraft;
import com.passage.agent.model.entity.AgentArticleVersionRecord;
import com.passage.agent.model.dto.article.ArticleState;
import com.passage.agent.service.AgentArticleArtifactService;
import com.passage.agent.service.AgentCheckpointService;
import com.passage.agent.service.AgentNodeExecutionService;
import com.passage.agent.service.AgentRunService;
import com.passage.agent.service.WorkflowRecoveryService;
import com.passage.agent.service.ArticleService;
import com.passage.agent.manager.SseEmitterManager;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Collections;

/** Durable continuation from the P3 quality gate into image delivery. */
@Service
public class ContentQualityContinuationService {
    private static final String NODE = "content-quality-accepted";
    private final AgentCheckpointService checkpoints;
    private final WorkflowRecoveryService recovery;
    private final P3ContentRecoveryAdapter snapshots;
    private final ApprovedContentImageExecutor images;
    private final AgentNodeExecutionService nodes;
    private final AgentArticleArtifactService artifacts;
    private final AgentRunService runs;
    private final ArticleService articles;
    private final SseEmitterManager sse;
    private final ObjectMapper objectMapper;

    public ContentQualityContinuationService(AgentCheckpointService checkpoints, WorkflowRecoveryService recovery,
                                             P3ContentCheckpointCodec codec, ApprovedContentImageExecutor images,
                                             AgentNodeExecutionService nodes, AgentArticleArtifactService artifacts,
                                             AgentRunService runs, ArticleService articles, SseEmitterManager sse, ObjectMapper objectMapper) {
        this.checkpoints = checkpoints; this.recovery = recovery; this.snapshots = new P3ContentRecoveryAdapter(codec);
        this.images = images; this.nodes = nodes; this.artifacts = artifacts; this.runs = runs; this.articles = articles; this.sse = sse; this.objectMapper = objectMapper;
    }

    public ContentQualityContinuationResult continueDelivery(String runId) {
        var ready = checkpoints.findReadyCheckpoint(runId, NODE);
        AtomicReference<ContentQualityContinuationResult> result = new AtomicReference<>();
        recovery.resume(ready.checkpointId(), checkpoint -> {
            P3ContentCheckpointSnapshot snapshot = snapshots.snapshot(checkpoint);
            List<String> deferredSse = Collections.synchronizedList(new java.util.ArrayList<>());
            NodeExecutionOutcome outcome = nodes.executeOnce(runId, "accepted-content-image-delivery", checkpoint.stateVersion(),
                    () -> serialize(images.execute(new ApprovedContentImageRequest(runId, snapshot.deliveryContext().mainTitle(),
                            snapshot.deliveryContext().subTitle(), snapshot.deliveryContext().style(),
                            snapshot.deliveryContext().enabledImageMethods(), snapshot.markdown()), deferredSse::add)));
            ApprovedContentImageResult imageResult = deserialize(outcome.resultSnapshot());
            persistDeliveryVersion(snapshot, imageResult);
            ArticleState state = new ArticleState();
            state.setContent(snapshot.markdown()); state.setFullContent(imageResult.fullContent());
            state.setImageRequirements(imageResult.imageRequirements()); state.setImages(imageResult.images());
            ArticleState.TitleResult title = new ArticleState.TitleResult();
            title.setMainTitle(snapshot.deliveryContext().mainTitle()); title.setSubTitle(snapshot.deliveryContext().subTitle());
            state.setTitle(title);
            articles.saveArticleContent(runId, state);
            AgentRun completed = runs.getDomain(runId).transitionTo(AgentRunStatus.COMPLETED, Instant.now());
            runs.sync(completed, "article-completed");
            deferredSse.forEach(message -> sse.send(runId, message));
            sse.complete(runId);
            result.set(new ContentQualityContinuationResult(runId, "ARTICLE_COMPLETED", imageResult.fullContent()));
        });
        return result.get();
    }

    private void persistDeliveryVersion(P3ContentCheckpointSnapshot snapshot, ApprovedContentImageResult imageResult) {
        List<AgentArticleVersionRecord> versions = artifacts.listVersions(snapshot.runId());
        AgentArticleVersionRecord parent = versions.stream().filter(version -> version.getVersion() == snapshot.articleVersion())
                .findFirst().orElseThrow(() -> new IllegalStateException("Quality version is missing for image delivery"));
        int deliveryVersion = snapshot.articleVersion() + 1;
        ArticleVersion delivery = new ArticleVersion(deliveryVersion, snapshot.articleVersion(), "image delivery",
                List.of(new SectionDraft(0, "final-merged-delivery", imageResult.fullContent(), List.of())), Instant.now());
        ArtifactManifest manifest = new ArtifactManifest(snapshot.runId(), imageResult.images().stream()
                .map(image -> new ArtifactManifest.Artifact("image-v" + deliveryVersion + "-" + image.getPosition(), ArtifactType.IMAGE,
                        image.getUrl(), sha256(image.getUrl()))).collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(), list -> { list.add(new ArtifactManifest.Artifact("article-markdown-v" + deliveryVersion,
                                ArtifactType.ARTICLE_MARKDOWN, "run://" + snapshot.runId() + "/article/v" + deliveryVersion + "/article.md", sha256(imageResult.fullContent()))); return list; })), Instant.now());
        artifacts.publish(snapshot.runId(), delivery, manifest);
    }

    private String serialize(ApprovedContentImageResult result) { try { return objectMapper.writeValueAsString(result); } catch (JsonProcessingException e) { throw new IllegalStateException("Cannot persist image delivery", e); } }
    private ApprovedContentImageResult deserialize(String json) { try { return objectMapper.readValue(json, ApprovedContentImageResult.class); } catch (JsonProcessingException e) { throw new IllegalStateException("Image delivery snapshot is invalid", e); } }
    private String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); } }
}
