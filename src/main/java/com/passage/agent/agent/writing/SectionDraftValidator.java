package com.passage.agent.agent.writing;

import java.util.Set;
import java.util.stream.Collectors;

/** Verifies a Writer cannot invent citation IDs or omit task-mandated evidence. */
public final class SectionDraftValidator {
    public void validate(SectionWriterRequest request, SectionDraft draft) {
        if (draft == null) throw new IllegalArgumentException("SectionDraft must not be null");
        if (draft.sectionIndex() != request.task().sectionIndex() || !draft.sectionId().equals(request.task().id())) {
            throw new IllegalArgumentException("SectionDraft does not match its SectionTask");
        }
        Set<String> availableSourceIds = request.research().sources().stream()
                .map(source -> source.sourceId()).collect(Collectors.toSet());
        Set<String> citations = Set.copyOf(draft.citationSourceIds());
        if (!availableSourceIds.containsAll(citations)) {
            throw new IllegalArgumentException("SectionDraft cites an unknown research source");
        }
        if (!citations.containsAll(request.task().requiredSourceIds())) {
            throw new IllegalArgumentException("SectionDraft omitted required research citations");
        }
    }
}
