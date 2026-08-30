package com.passage.agent.agent.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Serialization boundary for the allow-listed P3 recovery snapshot. */
@Component
public class P3ContentCheckpointCodec {
    private final ObjectMapper objectMapper;
    public P3ContentCheckpointCodec(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    public String write(P3ContentCheckpointSnapshot snapshot) {
        try { return objectMapper.writeValueAsString(snapshot); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Unable to serialize P3 checkpoint", e); }
    }
    public P3ContentCheckpointSnapshot read(String json) {
        try { return objectMapper.readValue(json, P3ContentCheckpointSnapshot.class); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("P3 checkpoint snapshot is invalid", e); }
    }
}
