package com.passage.agent.agent.artifact;

import com.passage.agent.agent.writing.SectionDraft;
import com.passage.agent.agent.writing.SectionFanIn;

import java.time.Instant;
import java.util.List;

/** Immutable article revision; parentVersion makes the version lineage explicit. */
public record ArticleVersion(
        int version,
        Integer parentVersion,
        String changeReason,
        List<SectionDraft> drafts,
        Instant createdAt
) {
    public ArticleVersion {
        if (version <= 0 || (version == 1 && parentVersion != null) || (version > 1 && parentVersion == null)) {
            throw new IllegalArgumentException("ArticleVersion parent relationship is invalid");
        }
        if (changeReason == null || changeReason.isBlank() || createdAt == null) {
            throw new IllegalArgumentException("changeReason and createdAt must not be blank/null");
        }
        drafts = new SectionFanIn().merge(drafts);
    }
}
