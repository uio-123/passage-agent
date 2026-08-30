package com.passage.agent.agent.workflow;

import com.passage.agent.agent.research.ResearchBundle;
import com.passage.agent.agent.writing.SectionTask;

import java.util.List;

/** Typed hand-off from an approved legacy outline to the P3 quality-writing loop. */
public record ApprovedOutlineWritingRequest(
        String runId,
        long stateVersion,
        String topic,
        String style,
        List<SectionTask> sectionTasks,
        ResearchBundle research,
        boolean factEnhancementAvailable
) {
    public ApprovedOutlineWritingRequest {
        requireText(runId, "runId");
        requireText(topic, "topic");
        if (stateVersion < 0 || research == null) throw new IllegalArgumentException("stateVersion and research are invalid");
        sectionTasks = sectionTasks == null ? List.of() : List.copyOf(sectionTasks);
        if (sectionTasks.isEmpty() || sectionTasks.stream().map(SectionTask::id).distinct().count() != sectionTasks.size()
                || sectionTasks.stream().map(SectionTask::sectionIndex).distinct().count() != sectionTasks.size()) {
            throw new IllegalArgumentException("sectionTasks must be non-empty with unique ids and indexes");
        }
        if (factEnhancementAvailable != !research.sources().isEmpty()) {
            throw new IllegalArgumentException("factEnhancementAvailable must reflect registered research sources");
        }
    }
    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
