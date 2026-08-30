package com.passage.agent.agent.revision;

import com.passage.agent.agent.review.ReviewIssue;
import com.passage.agent.agent.writing.SectionDraft;
import com.passage.agent.agent.writing.SectionWriterRequest;

import java.util.List;

/** Bounded Revision input: one existing section and only the Gate findings for that section. */
public record SectionRevisionRequest(
        SectionWriterRequest writerRequest,
        SectionDraft originalDraft,
        List<ReviewIssue> issues
) {
    public SectionRevisionRequest {
        if (writerRequest == null || originalDraft == null) {
            throw new IllegalArgumentException("writerRequest and originalDraft must not be null");
        }
        if (originalDraft.sectionIndex() != writerRequest.task().sectionIndex()
                || !originalDraft.sectionId().equals(writerRequest.task().id())) {
            throw new IllegalArgumentException("originalDraft does not match its SectionTask");
        }
        issues = issues == null ? List.of() : List.copyOf(issues);
        if (issues.isEmpty() || issues.stream().anyMatch(issue -> !originalDraft.sectionId().equals(issue.sectionId()))) {
            throw new IllegalArgumentException("Revision issues must be non-empty and belong to the original section");
        }
    }
}
