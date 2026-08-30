package com.passage.agent.agent.revision;

import com.passage.agent.agent.writing.SectionDraft;

import java.util.List;

/** Stable article draft set after one bounded local revision round. */
public record RevisionResult(List<SectionDraft> drafts, List<String> revisedSectionIds, int completedRevisionRounds) {
    public RevisionResult {
        drafts = drafts == null ? List.of() : List.copyOf(drafts);
        revisedSectionIds = revisedSectionIds == null ? List.of() : List.copyOf(revisedSectionIds);
        if (completedRevisionRounds < 0) {
            throw new IllegalArgumentException("completedRevisionRounds must not be negative");
        }
    }
}
