package com.passage.agent.agent.writing;

import java.util.Comparator;
import java.util.List;

/** Stable section aggregation independent of concurrent Writer completion order. */
public final class SectionFanIn {
    public List<SectionDraft> merge(List<SectionDraft> drafts) {
        List<SectionDraft> safeDrafts = drafts == null ? List.of() : List.copyOf(drafts);
        if (safeDrafts.stream().map(SectionDraft::sectionId).distinct().count() != safeDrafts.size()
                || safeDrafts.stream().map(SectionDraft::sectionIndex).distinct().count() != safeDrafts.size()) {
            throw new IllegalArgumentException("Section drafts must have unique ids and section indexes");
        }
        return safeDrafts.stream().sorted(Comparator.comparingInt(SectionDraft::sectionIndex)).toList();
    }
}
