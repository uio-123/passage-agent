package com.passage.agent.agent.skill;

import java.util.Map;
import java.util.Set;

/** Small, declarative field schema for a Skill hand-off; it never evaluates user-supplied code. */
public record SkillSchema(Set<String> requiredFields, Set<String> allowedFields) {
    public SkillSchema {
        requiredFields = normalize(requiredFields, "requiredFields");
        allowedFields = normalize(allowedFields, "allowedFields");
        if (!allowedFields.containsAll(requiredFields)) {
            throw new IllegalArgumentException("allowedFields must contain every required field");
        }
    }

    public void validate(Map<String, ?> values, String phase) {
        Map<String, ?> safeValues = values == null ? Map.of() : Map.copyOf(values);
        if (!safeValues.keySet().containsAll(requiredFields)) {
            throw new IllegalArgumentException(phase + " is missing required Skill fields");
        }
        if (!allowedFields.containsAll(safeValues.keySet())) {
            throw new IllegalArgumentException(phase + " contains unknown Skill fields");
        }
        if (safeValues.values().stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(phase + " must not contain null Skill fields");
        }
    }

    private static Set<String> normalize(Set<String> fields, String name) {
        if (fields == null) return Set.of();
        Set<String> safeFields = Set.copyOf(fields);
        if (safeFields.stream().anyMatch(field -> field == null || field.isBlank())) {
            throw new IllegalArgumentException(name + " must contain non-blank field names");
        }
        return safeFields;
    }
}
