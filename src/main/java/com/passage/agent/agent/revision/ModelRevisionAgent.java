package com.passage.agent.agent.revision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passage.agent.agent.llm.AiModelPort;
import com.passage.agent.agent.llm.StructuredJsonExtractor;
import com.passage.agent.agent.writing.SectionDraft;
import com.passage.agent.agent.writing.SectionDraftValidator;
import org.springframework.stereotype.Component;
import java.util.List;

/** Production local revision adapter; prompt scope contains one section only. */
@Component
public class ModelRevisionAgent implements RevisionAgent {
    private final AiModelPort model; private final ObjectMapper objectMapper; private final SectionDraftValidator validator = new SectionDraftValidator();
    public ModelRevisionAgent(AiModelPort model, ObjectMapper objectMapper) { this.model = model; this.objectMapper = objectMapper; }
    @Override public SectionDraft revise(SectionRevisionRequest request) {
        Response response = read("Revise only section " + request.originalDraft().sectionId() + ". Return JSON only: {\"markdown\":string,\"citationSourceIds\":[string]}. Original="
                + request.originalDraft().markdown() + "; issues=" + request.issues() + "; registered sources=" + request.writerRequest().research().sources()
                + ". Do not modify any other section or invent citation IDs.", Response.class);
        SectionDraft draft = new SectionDraft(request.originalDraft().sectionIndex(), request.originalDraft().sectionId(), response.markdown(), response.citationSourceIds());
        validator.validate(request.writerRequest(), draft);
        return draft;
    }
    private <T> T read(String prompt, Class<T> type) { try { return objectMapper.readValue(StructuredJsonExtractor.extract(model.complete(prompt)), type); } catch (Exception e) { throw new IllegalArgumentException("Revision model output is not valid structured JSON", e); } }
    public record Response(String markdown, List<String> citationSourceIds) { }
}
