package com.passage.agent.agent.tool;

import java.time.Duration;
import java.util.List;

/** Sanitized result returned by a read-only research tool. */
public record ToolCallResult(
        ToolId toolId,
        List<SourceCandidate> sources,
        Duration elapsed,
        int retries,
        int responseBytes
) {
    public ToolCallResult {
        if (toolId == null) {
            throw new IllegalArgumentException("toolId must not be null");
        }
        sources = sources == null ? List.of() : List.copyOf(sources);
        if (elapsed == null || elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must be non-negative");
        }
        if (retries < 0 || responseBytes < 0) {
            throw new IllegalArgumentException("tool result counters must be non-negative");
        }
    }

    public ToolCallResult(ToolId toolId, List<SourceCandidate> sources, Duration elapsed) {
        this(toolId, sources, elapsed, 0, 0);
    }

    public record SourceCandidate(String url, String title, String publisher, String summary, String contentHash) {
        public SourceCandidate {
            requireText(url, "url");
            requireText(title, "title");
            requireText(summary, "summary");
            requireText(contentHash, "contentHash");
        }

        private static void requireText(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
        }
    }
}
