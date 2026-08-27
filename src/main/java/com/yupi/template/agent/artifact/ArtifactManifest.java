package com.yupi.template.agent.artifact;

import java.time.Instant;
import java.util.List;

/** Immutable list of deliverables produced by a workflow run. */
public record ArtifactManifest(String runId, List<Artifact> artifacts, Instant createdAt) {
    public ArtifactManifest {
        requireText(runId, "runId");
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }

    public ArtifactManifest add(Artifact artifact) {
        List<Artifact> updated = new java.util.ArrayList<>(artifacts);
        updated.add(artifact);
        return new ArtifactManifest(runId, updated, createdAt);
    }

    public record Artifact(String artifactId, ArtifactType type, String location, String sha256) {
        public Artifact {
            requireText(artifactId, "artifactId");
            if (type == null) {
                throw new IllegalArgumentException("type must not be null");
            }
            requireText(location, "location");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
