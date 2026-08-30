package com.passage.agent.agent.writing;

import com.passage.agent.agent.research.ResearchBundle;

/** The bounded evidence hand-off from P2 research to an individual P3 Writer. */
public record SectionWriterRequest(SectionTask task, ResearchBundle research) {
    public SectionWriterRequest {
        if (task == null || research == null) throw new IllegalArgumentException("task and research must not be null");
        java.util.Set<String> availableSourceIds = research.sources().stream()
                .map(com.passage.agent.agent.research.ResearchSource::sourceId).collect(java.util.stream.Collectors.toSet());
        if (!availableSourceIds.containsAll(task.requiredSourceIds())) {
            throw new IllegalArgumentException("SectionTask requires sources not present in the ResearchBundle");
        }
    }
}
