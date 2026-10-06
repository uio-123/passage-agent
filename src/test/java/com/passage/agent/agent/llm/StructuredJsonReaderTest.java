package com.passage.agent.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredJsonReaderTest {

    @Test
    void acceptsLiteralNewlinesInsideModelJsonStrings() throws Exception {
        Response response = StructuredJsonReader.read(new ObjectMapper(),
                "{\"markdown\":\"line one\nline two\",\"citationSourceIds\":[]}", Response.class);

        assertThat(response.markdown()).isEqualTo("line one\nline two");
    }

    private record Response(String markdown, java.util.List<String> citationSourceIds) { }
}
