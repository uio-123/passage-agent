package com.passage.agent.agent.research;

import com.passage.agent.agent.tool.ToolId;

import java.util.List;
import java.util.Set;

/** Controlled research input: Web Reader may only receive a registered Search result URL. */
public record ResearchRequest(
        String query,
        List<RegisteredSearchResult> registeredSearchResults,
        Set<ToolId> allowedTools,
        int callBudget
) {
    public ResearchRequest {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query must not be blank");
        registeredSearchResults = registeredSearchResults == null ? List.of() : List.copyOf(registeredSearchResults);
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        if (callBudget < 1) throw new IllegalArgumentException("callBudget must be positive");
    }
}
