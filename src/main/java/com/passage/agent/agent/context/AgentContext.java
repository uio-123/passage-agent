package com.passage.agent.agent.context;

import java.util.List;
import java.util.Objects;

/** Role-scoped context assembled for one agent invocation. */
public record AgentContext(
        String runId,
        Role role,
        List<Item> items,
        Budget budget
) {
    public AgentContext {
        requireText(runId, "runId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(budget, "budget");
        items = items == null ? List.of() : List.copyOf(items);
        if (items.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("context items must not contain null");
        }
        int estimated = items.stream().mapToInt(Item::estimatedTokens).sum();
        if (estimated > budget.availableEstimatedTokens()) {
            throw new IllegalArgumentException("context exceeds its available budget");
        }
    }

    public int estimatedTokens() {
        return items.stream().mapToInt(Item::estimatedTokens).sum();
    }

    public enum Role {
        RESEARCH,
        WRITER,
        REVIEWER,
        IMAGE
    }

    public enum Key {
        USER_REQUEST,
        RESEARCH_GOAL,
        EXISTING_SOURCES,
        CONSTRAINTS,
        CURRENT_CHAPTER,
        OUTLINE,
        RESEARCH_RESULT,
        REVIEWER_FEEDBACK,
        USER_PREFERENCE,
        ARTIFACT_REFS,
        CURRENT_DRAFT,
        QUALITY_RUBRIC,
        VISUAL_DESCRIPTION,
        IMAGE_STYLE,
        EXISTING_IMAGES
    }

    public enum Priority {
        REQUIRED,
        HIGH,
        NORMAL,
        LOW
    }

    public record Item(Key key, String value, Priority priority, int estimatedTokens) {
        public Item {
            Objects.requireNonNull(key, "key");
            requireText(value, "value");
            Objects.requireNonNull(priority, "priority");
            if (estimatedTokens < 1) {
                throw new IllegalArgumentException("estimatedTokens must be positive");
            }
        }

        public static Item of(Key key, String value, Priority priority) {
            return new Item(key, value, priority, estimateTokens(value));
        }
    }

    public record Budget(int maxEstimatedTokens, int reservedEstimatedTokens) {
        public Budget {
            if (maxEstimatedTokens < 1 || reservedEstimatedTokens < 0
                    || reservedEstimatedTokens >= maxEstimatedTokens) {
                throw new IllegalArgumentException("context budget is invalid");
            }
        }

        public int availableEstimatedTokens() {
            return maxEstimatedTokens - reservedEstimatedTokens;
        }
    }

    private static int estimateTokens(String value) {
        return Math.max(1, (value.length() + 3) / 4);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
