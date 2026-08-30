package com.passage.agent.agent.skill;

import com.passage.agent.agent.tool.ToolId;

import java.util.List;
import java.util.Set;

/** Immutable, versioned declaration of a built-in content-production Skill. */
public record SkillDefinition(
        String id,
        String version,
        SkillSchema inputSchema,
        SkillSchema outputSchema,
        Set<ToolId> allowedTools,
        int maxToolCalls,
        List<String> acceptanceCriteria
) {
    public SkillDefinition {
        requireText(id, "id");
        requireText(version, "version");
        if (inputSchema == null || outputSchema == null) throw new IllegalArgumentException("Skill schemas must not be null");
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        if (maxToolCalls < 0) throw new IllegalArgumentException("maxToolCalls must not be negative");
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        if (acceptanceCriteria.isEmpty() || acceptanceCriteria.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("acceptanceCriteria must not be empty or blank");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
