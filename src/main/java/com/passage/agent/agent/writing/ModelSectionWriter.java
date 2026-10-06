package com.passage.agent.agent.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passage.agent.agent.llm.AiModelPort;
import com.passage.agent.agent.llm.StructuredJsonReader;
import com.passage.agent.agent.research.ResearchSource;
import org.springframework.stereotype.Component;

import java.util.List;

/** Production P3 writer using the shared model port and a strict structured output boundary. */
@Component
public class ModelSectionWriter implements SectionWriter {
    private final AiModelPort model;
    private final ObjectMapper objectMapper;
    private final SectionDraftValidator validator = new SectionDraftValidator();
    public ModelSectionWriter(AiModelPort model, ObjectMapper objectMapper) { this.model = model; this.objectMapper = objectMapper; }
    @Override public SectionDraft write(SectionWriterRequest request) {
        Response response = read(prompt(request), Response.class);
        SectionDraft draft = new SectionDraft(request.task().sectionIndex(), request.task().id(), response.markdown(), response.citationSourceIds());
        validator.validate(request, draft);
        return draft;
    }
    private String prompt(SectionWriterRequest request) {
        String sources = request.research().sources().stream().map(source -> source.sourceId() + " | " + source.title() + " | " + source.summary())
                .collect(java.util.stream.Collectors.joining("\n"));
        return "Write exactly one article section. Return JSON only: {\"markdown\":string,\"citationSourceIds\":[string]}. "
                + "Section id=" + request.task().id() + "; heading=" + request.task().heading() + "; instruction=" + request.task().instruction()
                + "; required citation IDs=" + request.task().requiredSourceIds() + "; registered sources:\n" + sources
                + "\nNever invent citation IDs or URLs.";
    }
    private <T> T read(String prompt, Class<T> type) {
        try { return StructuredJsonReader.read(objectMapper, model.complete(prompt), type); }
        catch (Exception exception) { throw new IllegalArgumentException("Writer model output is not valid structured JSON", exception); }
    }
    public record Response(String markdown, List<String> citationSourceIds) { }
}
