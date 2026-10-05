package com.passage.agent.agent.llm;

/** Extracts the first JSON object or array from a model response, including fenced JSON. */
public final class StructuredJsonExtractor {

    private StructuredJsonExtractor() {
    }

    public static String extract(String response) {
        if (response == null) {
            return null;
        }
        String value = response.trim();
        if (value.startsWith("```")) {
            int firstLineEnd = value.indexOf('\n');
            if (firstLineEnd >= 0) {
                value = value.substring(firstLineEnd + 1).trim();
            }
            if (value.endsWith("```")) {
                value = value.substring(0, value.length() - 3).trim();
            }
        }
        int objectStart = value.indexOf('{');
        int objectEnd = value.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return value.substring(objectStart, objectEnd + 1);
        }
        int arrayStart = value.indexOf('[');
        int arrayEnd = value.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return value.substring(arrayStart, arrayEnd + 1);
        }
        return value;
    }
}
