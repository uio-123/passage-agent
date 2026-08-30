package com.passage.agent.agent.research;

import com.passage.agent.agent.tool.ToolAuthorization;
import com.passage.agent.agent.tool.ToolCallRequest;
import com.passage.agent.agent.tool.ToolId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResearchToolContractTest {

    @Test
    void acceptsBoundedTraceableResearchSource() {
        ResearchSource source = new ResearchSource("source-1", "https://example.com/article", "Example", "Publisher",
                Instant.parse("2026-08-29T00:00:00Z"), "sha256", "A bounded source summary", "research query",
                ResearchSourceStatus.VERIFIED);

        assertDoesNotThrow(() -> new ResearchBundle("run-1", java.util.List.of(source),
                java.util.List.of("Supported fact"), java.util.List.of()));
    }

    @Test
    void rejectsMissingSourceUrlAndOversizedSummary() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchSource("source-1", "", "Example", null,
                Instant.now(), "sha256", "summary", "query", ResearchSourceStatus.VERIFIED));
        assertThrows(IllegalArgumentException.class, () -> new ResearchSource("source-1", "https://example.com", "Example", null,
                Instant.now(), "sha256", "x".repeat(ResearchSource.MAX_SUMMARY_LENGTH + 1), "query", ResearchSourceStatus.VERIFIED));
    }

    @Test
    void rejectsUnauthorizedToolAndInvalidCallBudget() {
        ToolCallRequest unauthorized = new ToolCallRequest("run-1", ToolId.WEB_READER, "https://example.com",
                Set.of(ToolId.SEARCH), 1);
        assertThrows(IllegalArgumentException.class, () -> ToolAuthorization.requireAllowed(unauthorized));
        assertThrows(IllegalArgumentException.class, () -> new ToolCallRequest("run-1", ToolId.SEARCH, "topic",
                Set.of(ToolId.SEARCH), 0));
    }
}
