package com.passage.agent.agent.research;

import java.util.List;

/** Research hand-off that makes sources and unsupported claims explicit to downstream agents. */
public record ResearchBundle(
        String runId,
        List<ResearchSource> sources,
        List<String> supportedFacts,
        List<String> unresolvedClaims
) {
    public ResearchBundle {
        requireText(runId, "runId");
        sources = sources == null ? List.of() : List.copyOf(sources);
        supportedFacts = supportedFacts == null ? List.of() : List.copyOf(supportedFacts);
        unresolvedClaims = unresolvedClaims == null ? List.of() : List.copyOf(unresolvedClaims);
        if (sources.isEmpty() && unresolvedClaims.isEmpty()) {
            throw new IllegalArgumentException("A research bundle must contain sources or unresolved claims");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
