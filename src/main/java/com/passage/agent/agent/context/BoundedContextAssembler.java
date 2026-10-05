package com.passage.agent.agent.context;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic role filtering plus a size budget; compression is not implemented here. */
@Service
public class BoundedContextAssembler implements ContextAssembler {

    private static final Map<AgentContext.Role, Set<AgentContext.Key>> ALLOWED = allowedKeys();
    private static final Map<AgentContext.Role, Set<AgentContext.Key>> REQUIRED = requiredKeys();

    @Override
    public AgentContext assemble(ContextAssemblyRequest request) {
        Set<AgentContext.Key> allowed = ALLOWED.get(request.role());
        List<AgentContext.Item> selected = new ArrayList<>();
        int remaining = request.budget().availableEstimatedTokens();
        List<AgentContext.Item> candidates = request.candidates().stream()
                .filter(item -> allowed.contains(item.key()))
                .sorted(Comparator.comparingInt(item -> item.priority().ordinal()))
                .toList();
        for (AgentContext.Item item : candidates) {
            if (item.estimatedTokens() <= remaining) {
                selected.add(item);
                remaining -= item.estimatedTokens();
            } else if (item.priority() == AgentContext.Priority.REQUIRED) {
                throw new IllegalArgumentException("required context exceeds its budget: " + item.key());
            }
        }
        Set<AgentContext.Key> required = REQUIRED.get(request.role());
        Set<AgentContext.Key> selectedKeys = selected.stream()
                .map(AgentContext.Item::key)
                .collect(java.util.stream.Collectors.toSet());
        if (!selectedKeys.containsAll(required)) {
            EnumSet<AgentContext.Key> missing = EnumSet.noneOf(AgentContext.Key.class);
            missing.addAll(required);
            missing.removeAll(selectedKeys);
            throw new IllegalArgumentException("missing required context: " + missing);
        }
        return new AgentContext(request.runId(), request.role(), selected, request.budget());
    }

    private static Map<AgentContext.Role, Set<AgentContext.Key>> allowedKeys() {
        Map<AgentContext.Role, Set<AgentContext.Key>> result = new EnumMap<>(AgentContext.Role.class);
        result.put(AgentContext.Role.RESEARCH, Set.of(
                AgentContext.Key.USER_REQUEST,
                AgentContext.Key.RESEARCH_GOAL,
                AgentContext.Key.EXISTING_SOURCES,
                AgentContext.Key.CONSTRAINTS));
        result.put(AgentContext.Role.WRITER, Set.of(
                AgentContext.Key.USER_REQUEST,
                AgentContext.Key.CURRENT_CHAPTER,
                AgentContext.Key.OUTLINE,
                AgentContext.Key.RESEARCH_RESULT,
                AgentContext.Key.REVIEWER_FEEDBACK,
                AgentContext.Key.USER_PREFERENCE,
                AgentContext.Key.ARTIFACT_REFS));
        result.put(AgentContext.Role.REVIEWER, Set.of(
                AgentContext.Key.CURRENT_DRAFT,
                AgentContext.Key.RESEARCH_RESULT,
                AgentContext.Key.QUALITY_RUBRIC));
        result.put(AgentContext.Role.IMAGE, Set.of(
                AgentContext.Key.CURRENT_CHAPTER,
                AgentContext.Key.VISUAL_DESCRIPTION,
                AgentContext.Key.IMAGE_STYLE,
                AgentContext.Key.EXISTING_IMAGES));
        return Map.copyOf(result);
    }

    private static Map<AgentContext.Role, Set<AgentContext.Key>> requiredKeys() {
        Map<AgentContext.Role, Set<AgentContext.Key>> result = new EnumMap<>(AgentContext.Role.class);
        result.put(AgentContext.Role.RESEARCH, Set.of(
                AgentContext.Key.USER_REQUEST,
                AgentContext.Key.RESEARCH_GOAL));
        result.put(AgentContext.Role.WRITER, Set.of(
                AgentContext.Key.USER_REQUEST,
                AgentContext.Key.CURRENT_CHAPTER));
        result.put(AgentContext.Role.REVIEWER, Set.of(AgentContext.Key.CURRENT_DRAFT));
        result.put(AgentContext.Role.IMAGE, Set.of(
                AgentContext.Key.CURRENT_CHAPTER,
                AgentContext.Key.VISUAL_DESCRIPTION));
        return Map.copyOf(result);
    }
}
