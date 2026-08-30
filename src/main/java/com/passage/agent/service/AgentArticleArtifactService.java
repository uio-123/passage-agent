package com.passage.agent.service;

import com.passage.agent.agent.artifact.ArticleVersion;
import com.passage.agent.agent.artifact.ArtifactManifest;
import com.passage.agent.model.entity.AgentArticleVersionRecord;
import com.passage.agent.model.entity.AgentArtifactRecord;

import java.util.List;

/** Durable append-only boundary for P3 article versions and their manifest entries. */
public interface AgentArticleArtifactService {
    AgentArticleVersionRecord publish(String runId, ArticleVersion version, ArtifactManifest manifest);
    List<AgentArticleVersionRecord> listVersions(String runId);
    List<AgentArtifactRecord> listArtifacts(String runId, int articleVersion);
}
