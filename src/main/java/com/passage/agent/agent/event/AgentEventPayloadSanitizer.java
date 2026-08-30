package com.passage.agent.agent.event;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Prevents the observability channel from becoming a second storage location for sensitive content. */
public final class AgentEventPayloadSanitizer {
    private static final int MAX_VALUE_LENGTH = 512;
    private static final Set<String> FORBIDDEN = Set.of("authorization", "cookie", "token", "secret", "api-key",
            "prompt", "modeloutput", "responsebody", "webcontent", "statesnapshot");

    private AgentEventPayloadSanitizer() { }

    public static Map<String, String> sanitize(Map<String, String> payload) {
        if (payload == null || payload.isEmpty()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : payload.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank() || value == null || forbidden(key)) continue;
            String clean = value.replaceAll("[\\r\\n\\t]+", " ").trim();
            result.put(key, clean.length() > MAX_VALUE_LENGTH ? clean.substring(0, MAX_VALUE_LENGTH) : clean);
        }
        return Map.copyOf(result);
    }

    private static boolean forbidden(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return FORBIDDEN.stream().map(value -> value.replaceAll("[^a-z0-9]", ""))
                .anyMatch(normalized::contains);
    }
}
