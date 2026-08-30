package com.passage.agent.agent.writing;

import com.passage.agent.agent.research.ResearchBundle;
import com.passage.agent.agent.research.ResearchSource;
import com.passage.agent.agent.research.ResearchSourceStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SectionWritingContractTest {
    private final SectionDraftValidator validator = new SectionDraftValidator();
    private final ResearchBundle research = new ResearchBundle("research-run", List.of(
            new ResearchSource("source-1", "https://example.com/one", "One", "Example", Instant.EPOCH,
                    "hash-1", "Evidence one", "query", ResearchSourceStatus.VERIFIED),
            new ResearchSource("source-2", "https://example.com/two", "Two", "Example", Instant.EPOCH,
                    "hash-2", "Evidence two", "query", ResearchSourceStatus.VERIFIED)
    ), List.of("fact"), List.of());

    @Test
    void passesOnlyKnownAndRequiredCitationIdsToTheWriter() {
        SectionWriterRequest request = new SectionWriterRequest(task(1, "second", List.of("source-1")), research);
        SectionDraft draft = new SectionDraft(1, "second", "## Second", List.of("source-1"));

        validator.validate(request, draft);
    }

    @Test
    void rejectsMissingOrInventedCitationIds() {
        SectionWriterRequest request = new SectionWriterRequest(task(0, "first", List.of("source-1")), research);

        assertThatIllegalArgumentException().isThrownBy(() -> validator.validate(request,
                new SectionDraft(0, "first", "## First", List.of())));
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validate(request,
                new SectionDraft(0, "first", "## First", List.of("source-1", "invented"))));
        assertThatIllegalArgumentException().isThrownBy(() -> new SectionWriterRequest(task(0, "first", List.of("missing")), research));
    }

    @Test
    void mergesConcurrentDraftResultsByIndexAndRejectsDuplicateSections() {
        SectionFanIn fanIn = new SectionFanIn();
        List<SectionDraft> merged = fanIn.merge(List.of(
                new SectionDraft(2, "third", "third", List.of()),
                new SectionDraft(0, "first", "first", List.of()),
                new SectionDraft(1, "second", "second", List.of())
        ));

        assertThat(merged).extracting(SectionDraft::sectionId).containsExactly("first", "second", "third");
        assertThatIllegalArgumentException().isThrownBy(() -> fanIn.merge(List.of(
                new SectionDraft(0, "first", "first", List.of()),
                new SectionDraft(0, "another", "another", List.of())
        )));
    }

    private static SectionTask task(int index, String id, List<String> requiredSourceIds) {
        return new SectionTask(index, id, "Heading " + id, "Write " + id, requiredSourceIds);
    }
}
