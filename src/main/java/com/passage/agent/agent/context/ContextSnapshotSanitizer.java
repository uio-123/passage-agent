package com.passage.agent.agent.context;
/** Cleans caller-owned safe summaries before persistence. */
public final class ContextSnapshotSanitizer {
    private static final int MAX = 1024;
    private ContextSnapshotSanitizer() { }
    public static String sanitize(String value) {
        if (value == null) return "未提供安全摘要";
        String result = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("(?i)(api[-_ ]?key|authorization|cookie|token|secret)\\s*[:=]\\s*\\S+", "[已脱敏]").trim();
        return result.length() > MAX ? result.substring(0, MAX) : result;
    }
}
