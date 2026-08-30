package com.passage.agent.agent.workflow;

import com.passage.agent.agent.research.ResearchBundle;
import com.passage.agent.agent.state.WorkflowState;
import com.passage.agent.agent.writing.SectionTask;
import com.passage.agent.model.dto.article.ArticleState;

import java.util.List;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/** Converts only a user-approved typed outline; no prompt text or URL is parsed as evidence. */
@Component
public final class ApprovedOutlineWritingRequestFactory {
    public ApprovedOutlineWritingRequest create(WorkflowState state, long stateVersion, ResearchBundle research) {
        if (state == null || state.draft().selectedTitle() == null || state.draft().outline() == null) {
            throw new IllegalArgumentException("An approved title and outline are required for P3 writing");
        }
        List<ArticleState.OutlineSection> sections = state.draft().outline().getSections();
        if (sections == null || sections.isEmpty()) throw new IllegalArgumentException("Approved outline must contain sections");
        ResearchBundle effectiveResearch = research == null
                ? new ResearchBundle(state.run().runId() + ":no-research", List.of(), List.of(), List.of("Fact enhancement was not requested"))
                : research;
        List<SectionTask> tasks = IntStream.range(0, sections.size()).mapToObj(index -> task(index, sections.get(index))).toList();
        return new ApprovedOutlineWritingRequest(state.run().runId(), stateVersion, state.input().topic(), state.input().style(),
                tasks, effectiveResearch, !effectiveResearch.sources().isEmpty());
    }

    private static SectionTask task(int index, ArticleState.OutlineSection section) {
        if (section == null || section.getTitle() == null || section.getTitle().isBlank()) {
            throw new IllegalArgumentException("Approved outline sections must have titles");
        }
        List<String> points = section.getPoints() == null ? List.of() : List.copyOf(section.getPoints());
        String instruction = points.isEmpty() ? "Write the approved section: " + section.getTitle()
                : "Write the approved section '" + section.getTitle() + "' covering: " + String.join("; ", points);
        return new SectionTask(index, "outline-section-" + index, section.getTitle(), instruction, List.of());
    }
}
