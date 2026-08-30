package com.passage.agent.agent.artifact;

import com.passage.agent.agent.revision.RevisionResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Append-only in-memory version chain; a persistence adapter can store each immutable entry. */
public record ArticleVersionChain(List<ArticleVersion> versions) {
    public ArticleVersionChain {
        versions = versions == null ? List.of() : List.copyOf(versions);
        for (int index = 0; index < versions.size(); index++) {
            ArticleVersion version = versions.get(index);
            if (version.version() != index + 1
                    || (index == 0 ? version.parentVersion() != null : !Integer.valueOf(index).equals(version.parentVersion()))) {
                throw new IllegalArgumentException("ArticleVersionChain must be contiguous and append-only");
            }
        }
    }

    public static ArticleVersionChain initial(List<com.passage.agent.agent.writing.SectionDraft> drafts, String reason, Instant createdAt) {
        return new ArticleVersionChain(List.of(new ArticleVersion(1, null, reason, drafts, createdAt)));
    }

    public ArticleVersionChain append(RevisionResult revision, String reason, Instant createdAt) {
        if (revision == null || versions.isEmpty()) throw new IllegalArgumentException("revision and initial version are required");
        List<ArticleVersion> updated = new ArrayList<>(versions);
        ArticleVersion latest = versions.getLast();
        updated.add(new ArticleVersion(latest.version() + 1, latest.version(), reason, revision.drafts(), createdAt));
        return new ArticleVersionChain(updated);
    }

    public ArticleVersion latest() {
        if (versions.isEmpty()) throw new IllegalStateException("ArticleVersionChain is empty");
        return versions.getLast();
    }
}
