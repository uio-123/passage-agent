package com.passage.agent.agent.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passage.agent.agent.llm.AiModelPort;
import com.passage.agent.agent.llm.StructuredJsonReader;
import com.passage.agent.agent.writing.SectionDraft;
import org.springframework.stereotype.Component;
import java.util.List;

/** Style reviewer returns findings, never rewritten Markdown. */
@Component
public class ModelStyleReviewer implements StyleReviewer {
    private final AiModelPort model; private final ObjectMapper objectMapper;
    public ModelStyleReviewer(AiModelPort model, ObjectMapper objectMapper) { this.model = model; this.objectMapper = objectMapper; }
    @Override public ReviewReport review(SectionDraft draft) {
        Response response = read("Review style for section " + draft.sectionId() + ". Return JSON only: {\"score\":0-100,\"issues\":[{\"sectionId\":string,\"severity\":\"BLOCKER|MAJOR|MINOR\",\"code\":string,\"message\":string}]}. Do not rewrite the section. Markdown=" + draft.markdown(), Response.class);
        List<ReviewIssue> issues = response.issues().stream()
                .map(issue -> new ReviewIssue(draft.sectionId(), issue.severity(), issue.code(), issue.message()))
                .toList();
        return new ReviewReport(ReviewType.STYLE, response.score(), issues);
    }
    private <T> T read(String prompt, Class<T> type) { try { return StructuredJsonReader.read(objectMapper, model.complete(prompt), type); } catch (Exception e) { throw new IllegalArgumentException("Style reviewer output is not valid structured JSON", e); } }
    public record Response(int score, List<ReviewIssue> issues) { }
}
