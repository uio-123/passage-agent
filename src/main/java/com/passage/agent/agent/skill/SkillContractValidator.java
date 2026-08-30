package com.passage.agent.agent.skill;

import com.passage.agent.agent.tool.ToolId;

import java.util.Map;
import java.util.Set;

/** Validates a resolved Skill at the application boundary before any agent or Tool can execute it. */
public final class SkillContractValidator {
    public void validateInvocation(SkillDefinition skill, Map<String, ?> input, Set<ToolId> requestedTools, int requestedToolCalls) {
        if (skill == null) throw new IllegalArgumentException("skill must not be null");
        skill.inputSchema().validate(input, "Skill input");
        Set<ToolId> safeTools = requestedTools == null ? Set.of() : Set.copyOf(requestedTools);
        if (!skill.allowedTools().containsAll(safeTools)) {
            throw new IllegalArgumentException("Skill requested an unauthorized Tool");
        }
        if (requestedToolCalls < 0 || requestedToolCalls > skill.maxToolCalls()) {
            throw new IllegalArgumentException("Skill Tool call budget exceeds its declared maximum");
        }
        if (requestedToolCalls > 0 && safeTools.isEmpty()) {
            throw new IllegalArgumentException("Skill Tool call budget requires an authorized Tool");
        }
    }

    public void validateOutput(SkillDefinition skill, Map<String, ?> output) {
        if (skill == null) throw new IllegalArgumentException("skill must not be null");
        skill.outputSchema().validate(output, "Skill output");
    }
}
