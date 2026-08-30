package com.passage.agent.agent.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passage.agent.agent.llm.AiModelPort;
import org.springframework.stereotype.Component;
import java.util.List;

/** Fact reviewer bounded to registered source IDs; no-source work is auditable but non-blocking. */
@Component
public class ModelFactChecker implements FactChecker {
    private final AiModelPort model; private final ObjectMapper objectMapper;
    public ModelFactChecker(AiModelPort model, ObjectMapper objectMapper) { this.model = model; this.objectMapper = objectMapper; }
    @Override public ReviewReport review(FactReviewRequest request) {
        if (request.research().sources().isEmpty()) return new ReviewReport(ReviewType.FACT, 100,
                List.of(new ReviewIssue(request.draft().sectionId(), ReviewSeverity.MINOR, "FACT_ENHANCEMENT_NOT_REQUESTED", "No registered research sources were requested")));
        Response response = read("Review factual support for section " + request.draft().sectionId() + ". Return JSON only: {\"score\":0-100,\"issues\":[{\"sectionId\":string,\"severity\":\"BLOCKER|MAJOR|MINOR\",\"code\":string,\"message\":string}]}. "
                + "Draft citations=" + request.draft().citationSourceIds() + "; registered sources=" + request.research().sources(), Response.class);
        ReviewReport report = new ReviewReport(ReviewType.FACT, response.score(), response.issues());
        if (report.issues().stream().anyMatch(issue -> !issue.sectionId().equals(request.draft().sectionId()))) throw new IllegalArgumentException("Fact reviewer returned a cross-section issue");
        return report;
    }
    private <T> T read(String prompt, Class<T> type) { try { return objectMapper.readValue(model.complete(prompt), type); } catch (Exception e) { throw new IllegalArgumentException("Fact reviewer output is not valid structured JSON", e); } }
    public record Response(int score, List<ReviewIssue> issues) { }
}
