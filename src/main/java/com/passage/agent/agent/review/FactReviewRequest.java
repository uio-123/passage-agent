package com.passage.agent.agent.review;

import com.passage.agent.agent.research.ResearchBundle;
import com.passage.agent.agent.writing.SectionDraft;

/** Fact review can reason only over Writer citation IDs and the bounded P2 ResearchBundle. */
public record FactReviewRequest(SectionDraft draft, ResearchBundle research) {
    public FactReviewRequest {
        if (draft == null || research == null) throw new IllegalArgumentException("draft and research must not be null");
        java.util.Set<String> sourceIds = research.sources().stream()
                .map(source -> source.sourceId()).collect(java.util.stream.Collectors.toSet());
        if (!sourceIds.containsAll(draft.citationSourceIds())) {
            throw new IllegalArgumentException("Fact review draft cites a source absent from its ResearchBundle");
        }
    }
}
