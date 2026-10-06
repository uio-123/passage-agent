package com.passage.agent.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passage.agent.agent.llm.AiModelPort;
import com.passage.agent.agent.research.ResearchBundle;
import com.passage.agent.agent.research.ResearchSource;
import com.passage.agent.agent.research.ResearchSourceStatus;
import com.passage.agent.agent.review.FactReviewRequest;
import com.passage.agent.agent.review.ModelFactChecker;
import com.passage.agent.agent.review.ModelStyleReviewer;
import com.passage.agent.agent.review.ReviewIssue;
import com.passage.agent.agent.revision.ModelRevisionAgent;
import com.passage.agent.agent.revision.SectionRevisionRequest;
import com.passage.agent.agent.writing.ModelSectionWriter;
import com.passage.agent.agent.writing.SectionDraft;
import com.passage.agent.agent.writing.SectionTask;
import com.passage.agent.agent.writing.SectionWriterRequest;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ModelP3AdaptersTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ResearchBundle research = new ResearchBundle("research", List.of(new ResearchSource("source-1", "https://example.com", "Source", null,
            Instant.EPOCH, "hash", "summary", "query", ResearchSourceStatus.VERIFIED)), List.of(), List.of("none"));

    @Test void writerAndRevisionRejectInventedCitations() {
        SectionWriterRequest request = new SectionWriterRequest(new SectionTask(0, "section", "Heading", "Write", List.of("source-1")), research);
        assertThatIllegalArgumentException().isThrownBy(() -> new ModelSectionWriter(model("{\"markdown\":\"text\",\"citationSourceIds\":[\"invented\"]}"), json).write(request));
        SectionRevisionRequest revision = new SectionRevisionRequest(request, new SectionDraft(0, "section", "old", List.of("source-1")),
                List.of(new com.passage.agent.agent.review.ReviewIssue("section", com.passage.agent.agent.review.ReviewSeverity.MAJOR, "STYLE", "fix")));
        assertThatIllegalArgumentException().isThrownBy(() -> new ModelRevisionAgent(model("{\"markdown\":\"new\",\"citationSourceIds\":[\"invented\"]}"), json).revise(revision));
    }

    @Test void factWithoutResearchIsAuditableAndStyleIssueIsCoercedToTheReviewedSection() {
        ResearchBundle none = new ResearchBundle("none", List.of(), List.of(), List.of("not requested"));
        var report = new ModelFactChecker(model("unused"), json).review(new FactReviewRequest(new SectionDraft(0, "section", "text", List.of()), none));
        assertThat(report.score()).isEqualTo(100);
        assertThat(report.issues()).singleElement().extracting(issue -> issue.code()).isEqualTo("FACT_ENHANCEMENT_NOT_REQUESTED");
        var style = new ModelStyleReviewer(model("{\"score\":90,\"issues\":[{\"sectionId\":\"other\",\"severity\":\"MAJOR\",\"code\":\"STYLE\",\"message\":\"fix\"}]}"), json)
                .review(new SectionDraft(0, "section", "text", List.of()));
        assertThat(style.issues()).singleElement().extracting(ReviewIssue::sectionId).isEqualTo("section");
    }

    private static AiModelPort model(String response) { return new AiModelPort() {
        public String complete(String prompt) { return response; }
        public Flux<String> stream(String prompt) { return Flux.empty(); }
        public <T> T completeStructured(String prompt, Type responseType) { throw new UnsupportedOperationException(); }
    }; }
}
