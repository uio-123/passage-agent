package com.passage.agent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.passage.agent.agent.artifact.ArticleVersion;
import com.passage.agent.agent.artifact.ArtifactManifest;
import com.passage.agent.mapper.AgentArticleVersionMapper;
import com.passage.agent.mapper.AgentArtifactMapper;
import com.passage.agent.model.entity.AgentArticleVersionRecord;
import com.passage.agent.model.entity.AgentArtifactRecord;
import com.passage.agent.service.AgentArticleArtifactService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Persists only immutable, validated E4 outputs; blob upload is deliberately outside this transaction. */
@Service
public class AgentArticleArtifactServiceImpl extends ServiceImpl<AgentArticleVersionMapper, AgentArticleVersionRecord>
        implements AgentArticleArtifactService {
    private final AgentArtifactMapper artifactMapper;
    private final ObjectMapper objectMapper;

    public AgentArticleArtifactServiceImpl(AgentArtifactMapper artifactMapper, ObjectMapper objectMapper) {
        this.artifactMapper = artifactMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AgentArticleVersionRecord publish(String runId, ArticleVersion version, ArtifactManifest manifest) {
        requireText(runId, "runId");
        if (version == null || manifest == null || !runId.equals(manifest.runId())) {
            throw new IllegalArgumentException("version, manifest and runId must agree");
        }
        String draftsSnapshot = asJson(version.drafts());
        AgentArticleVersionRecord existing = findVersion(runId, version.version());
        if (existing != null) {
            verifySameVersion(existing, version, draftsSnapshot);
            verifyArtifacts(runId, version.version(), manifest);
            return existing;
        }
        verifyExpectedParent(runId, version);
        AgentArticleVersionRecord record = new AgentArticleVersionRecord();
        record.setRunId(runId); record.setVersion(version.version()); record.setParentVersion(version.parentVersion());
        record.setChangeReason(version.changeReason()); record.setDraftsSnapshot(draftsSnapshot);
        record.setCreateTime(LocalDateTime.ofInstant(version.createdAt(), ZoneId.systemDefault()));
        try {
            save(record);
        } catch (DuplicateKeyException ignored) {
            AgentArticleVersionRecord repeated = findVersion(runId, version.version());
            if (repeated == null) throw ignored;
            verifySameVersion(repeated, version, draftsSnapshot);
            verifyArtifacts(runId, version.version(), manifest);
            return repeated;
        }
        for (ArtifactManifest.Artifact artifact : manifest.artifacts()) {
            AgentArtifactRecord artifactRecord = new AgentArtifactRecord();
            artifactRecord.setRunId(runId); artifactRecord.setArticleVersion(version.version());
            artifactRecord.setArtifactId(artifact.artifactId()); artifactRecord.setArtifactType(artifact.type().name());
            artifactRecord.setLocation(artifact.location()); artifactRecord.setSha256(artifact.sha256());
            artifactRecord.setCreateTime(LocalDateTime.ofInstant(manifest.createdAt(), ZoneId.systemDefault()));
            artifactMapper.insert(artifactRecord);
        }
        return record;
    }

    @Override public List<AgentArticleVersionRecord> listVersions(String runId) {
        requireText(runId, "runId");
        return list(QueryWrapper.create().eq("runId", runId).orderBy("version", true));
    }

    @Override public List<AgentArtifactRecord> listArtifacts(String runId, int articleVersion) {
        requireText(runId, "runId");
        if (articleVersion <= 0) throw new IllegalArgumentException("articleVersion must be positive");
        return artifactMapper.selectListByQuery(QueryWrapper.create().eq("runId", runId)
                .eq("articleVersion", articleVersion).orderBy("artifactId", true));
    }

    private void verifyExpectedParent(String runId, ArticleVersion version) {
        if (version.version() == 1) return;
        AgentArticleVersionRecord parent = findVersion(runId, version.version() - 1);
        if (parent == null || !Integer.valueOf(version.version() - 1).equals(version.parentVersion())) {
            throw new IllegalArgumentException("Article versions must be appended after their persisted parent");
        }
    }

    private void verifySameVersion(AgentArticleVersionRecord existing, ArticleVersion version, String draftsSnapshot) {
        if (!java.util.Objects.equals(existing.getParentVersion(), version.parentVersion())
                || !existing.getChangeReason().equals(version.changeReason())
                || !sameJson(existing.getDraftsSnapshot(), draftsSnapshot)) {
            throw new IllegalArgumentException("A persisted article version cannot be overwritten");
        }
    }

    private void verifyArtifacts(String runId, int version, ArtifactManifest manifest) {
        List<AgentArtifactRecord> existing = listArtifacts(runId, version);
        if (existing.size() != manifest.artifacts().size() || !existing.stream().allMatch(record -> manifest.artifacts().stream()
                .anyMatch(artifact -> artifact.artifactId().equals(record.getArtifactId())
                        && artifact.type().name().equals(record.getArtifactType())
                        && artifact.location().equals(record.getLocation()) && artifact.sha256().equals(record.getSha256())))) {
            throw new IllegalArgumentException("A persisted ArtifactManifest cannot be overwritten");
        }
    }

    private AgentArticleVersionRecord findVersion(String runId, int version) {
        return getOne(QueryWrapper.create().eq("runId", runId).eq("version", version));
    }

    private String asJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Article drafts must be serializable", exception); }
    }

    private boolean sameJson(String left, String right) {
        try { return objectMapper.readTree(left).equals(objectMapper.readTree(right)); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Persisted article drafts must be valid JSON", exception); }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
