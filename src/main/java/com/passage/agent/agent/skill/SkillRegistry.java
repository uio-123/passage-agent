package com.passage.agent.agent.skill;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Closed, version-exact registry. Duplicate versions are rejected rather than overwritten. */
public final class SkillRegistry {
    private final Map<SkillKey, SkillDefinition> definitions;

    public SkillRegistry(Collection<SkillDefinition> definitions) {
        Map<SkillKey, SkillDefinition> registered = new LinkedHashMap<>();
        if (definitions != null) {
            for (SkillDefinition definition : definitions) {
                if (definition == null || registered.putIfAbsent(new SkillKey(definition.id(), definition.version()), definition) != null) {
                    throw new IllegalArgumentException("Skill registry contains an invalid or duplicate definition");
                }
            }
        }
        this.definitions = Map.copyOf(registered);
    }

    public SkillDefinition required(String id, String version) {
        SkillDefinition definition = definitions.get(new SkillKey(id, version));
        if (definition == null) throw new IllegalArgumentException("Unknown Skill id or version");
        return definition;
    }

    public Collection<SkillDefinition> all() {
        return definitions.values();
    }

    private record SkillKey(String id, String version) { }
}
