package com.passage.agent.agent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reads model JSON with one controlled leniency for literal control characters in strings. */
public final class StructuredJsonReader {

    private StructuredJsonReader() {
    }

    public static <T> T read(ObjectMapper objectMapper, String response, Class<T> type)
            throws JsonProcessingException {
        String json = StructuredJsonExtractor.extract(response);
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException strictFailure) {
            ObjectMapper lenient = objectMapper.copy()
                    .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
            try {
                return lenient.readValue(json, type);
            } catch (JsonProcessingException lenientFailure) {
                strictFailure.addSuppressed(lenientFailure);
                throw strictFailure;
            }
        }
    }
}
