package com.passage.agent.agent.event;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AgentEventPayloadSanitizerTest {
    @Test
    void excludesSensitivePayloadFieldsAndBoundsSafeSummary() {
        String longValue = "x".repeat(600);
        Map<String, String> sanitized = AgentEventPayloadSanitizer.sanitize(Map.of(
                "status", "completed\n", "api-key", "must-not-leak", "promptVersion", "hidden", "summary", longValue));
        assertEquals("completed", sanitized.get("status"));
        assertEquals(512, sanitized.get("summary").length());
        assertFalse(sanitized.containsKey("api-key"));
        assertFalse(sanitized.containsKey("promptVersion"));
    }
}
