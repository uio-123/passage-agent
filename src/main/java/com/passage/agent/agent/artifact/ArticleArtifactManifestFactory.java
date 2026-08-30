package com.passage.agent.agent.artifact;

import com.passage.agent.agent.research.ResearchBundle;
import com.passage.agent.agent.research.ResearchSource;
import com.passage.agent.agent.review.QualityGateDecision;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

/** Builds stable references and content hashes; durable blob storage remains an infrastructure concern. */
public final class ArticleArtifactManifestFactory {
    public ArtifactManifest create(String runId, ArticleVersion article, ResearchBundle research,
                                   QualityGateDecision gateDecision, Instant createdAt) {
        if (article == null || research == null || gateDecision == null || createdAt == null) {
            throw new IllegalArgumentException("article, research, gateDecision and createdAt must not be null");
        }
        String articleBody = article.drafts().stream().map(draft -> draft.markdown()).collect(java.util.stream.Collectors.joining("\n\n"));
        String sourceBundle = research.sources().stream().map(ResearchSource::sourceId)
                .sorted().collect(java.util.stream.Collectors.joining("\n"));
        String qualityReport = gateDecision.decision() + "|" + gateDecision.completedRevisionRounds() + "|"
                + gateDecision.issues().stream().map(issue -> issue.sectionId() + ":" + issue.code()).sorted()
                .collect(java.util.stream.Collectors.joining(","));
        String prefix = "run://" + runId + "/article/v" + article.version() + "/";
        return new ArtifactManifest(runId, List.of(
                new ArtifactManifest.Artifact("article-markdown-v" + article.version(), ArtifactType.ARTICLE_MARKDOWN,
                        prefix + "article.md", sha256(articleBody)),
                new ArtifactManifest.Artifact("source-bundle-v" + article.version(), ArtifactType.SOURCE_BUNDLE,
                        prefix + "sources.json", sha256(sourceBundle)),
                new ArtifactManifest.Artifact("quality-report-v" + article.version(), ArtifactType.QUALITY_REPORT,
                        prefix + "quality.json", sha256(qualityReport))
        ), createdAt);
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte valueByte : bytes) result.append(String.format("%02x", valueByte));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
