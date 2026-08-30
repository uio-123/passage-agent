package com.passage.agent.agent.revision;

import com.passage.agent.agent.review.QualityGateDecision;
import com.passage.agent.agent.review.ReviewIssue;
import com.passage.agent.agent.writing.SectionDraft;
import com.passage.agent.agent.writing.SectionDraftValidator;
import com.passage.agent.agent.writing.SectionFanIn;
import com.passage.agent.agent.writing.SectionTask;
import com.passage.agent.agent.writing.SectionWriterRequest;
import com.passage.agent.agent.research.ResearchBundle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Applies a Gate revision decision without allowing an agent to rewrite unflagged sections. */
public final class SectionRevisionUseCase {
    private final SectionDraftValidator draftValidator;
    private final SectionFanIn fanIn;

    public SectionRevisionUseCase(SectionDraftValidator draftValidator, SectionFanIn fanIn) {
        if (draftValidator == null || fanIn == null) {
            throw new IllegalArgumentException("draftValidator and fanIn must not be null");
        }
        this.draftValidator = draftValidator;
        this.fanIn = fanIn;
    }

    public RevisionResult revise(
            List<SectionTask> tasks,
            List<SectionDraft> drafts,
            ResearchBundle research,
            QualityGateDecision gateDecision,
            RevisionAgent revisionAgent
    ) {
        if (research == null || gateDecision == null || revisionAgent == null) {
            throw new IllegalArgumentException("research, gateDecision and revisionAgent must not be null");
        }
        if (gateDecision.decision() != QualityGateDecision.Decision.REVISE) {
            throw new IllegalArgumentException("Local revision requires a REVISE quality-gate decision");
        }
        Map<String, SectionTask> tasksById = indexTasks(tasks);
        Map<String, SectionDraft> draftsById = indexDrafts(drafts);
        if (!tasksById.keySet().equals(draftsById.keySet())) {
            throw new IllegalArgumentException("Section tasks and drafts must have the same section ids");
        }

        Map<String, List<ReviewIssue>> issuesBySection = gateDecision.issues().stream()
                .collect(Collectors.groupingBy(ReviewIssue::sectionId, LinkedHashMap::new, Collectors.toList()));
        if (issuesBySection.isEmpty() || !draftsById.keySet().containsAll(issuesBySection.keySet())) {
            throw new IllegalArgumentException("Quality-gate issues must target existing sections");
        }

        Map<String, SectionDraft> updated = new LinkedHashMap<>(draftsById);
        for (Map.Entry<String, List<ReviewIssue>> entry : issuesBySection.entrySet()) {
            String sectionId = entry.getKey();
            SectionWriterRequest writerRequest = new SectionWriterRequest(tasksById.get(sectionId), research);
            SectionRevisionRequest request = new SectionRevisionRequest(writerRequest, draftsById.get(sectionId), entry.getValue());
            SectionDraft revised = revisionAgent.revise(request);
            draftValidator.validate(writerRequest, revised);
            updated.put(sectionId, revised);
        }
        return new RevisionResult(fanIn.merge(List.copyOf(updated.values())), List.copyOf(issuesBySection.keySet()),
                gateDecision.completedRevisionRounds());
    }

    private static Map<String, SectionTask> indexTasks(List<SectionTask> tasks) {
        List<SectionTask> safeTasks = tasks == null ? List.of() : List.copyOf(tasks);
        Map<String, SectionTask> byId = safeTasks.stream().collect(Collectors.toMap(SectionTask::id, task -> task,
                (left, right) -> { throw new IllegalArgumentException("Section tasks must have unique ids"); }, LinkedHashMap::new));
        Set<Integer> indexes = safeTasks.stream().map(SectionTask::sectionIndex).collect(Collectors.toSet());
        if (indexes.size() != safeTasks.size()) throw new IllegalArgumentException("Section tasks must have unique indexes");
        return byId;
    }

    private static Map<String, SectionDraft> indexDrafts(List<SectionDraft> drafts) {
        List<SectionDraft> safeDrafts = drafts == null ? List.of() : List.copyOf(drafts);
        return safeDrafts.stream().collect(Collectors.toMap(SectionDraft::sectionId, draft -> draft,
                (left, right) -> { throw new IllegalArgumentException("Section drafts must have unique ids"); }, LinkedHashMap::new));
    }
}
