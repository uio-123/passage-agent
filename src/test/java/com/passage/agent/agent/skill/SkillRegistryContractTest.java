package com.passage.agent.agent.skill;

import com.passage.agent.agent.tool.ToolId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SkillRegistryContractTest {
    private final SkillRegistry registry = new SkillRegistry(BuiltinSkillCatalog.definitions());
    private final SkillContractValidator validator = new SkillContractValidator();

    @Test
    void exposesAllFiveBuiltInSkillsByExactVersion() {
        assertThat(registry.all()).extracting(SkillDefinition::id)
                .containsExactlyInAnyOrder("research-brief", "longform-article", "fact-check", "visual-plan", "citation-format");
        assertThat(registry.required("research-brief", "1.0.0").allowedTools())
                .containsExactlyInAnyOrder(ToolId.SEARCH, ToolId.WEB_READER);
        assertThatIllegalArgumentException().isThrownBy(() -> registry.required("research-brief", "2.0.0"));
    }

    @Test
    void refusesSilentReplacementOfAnExistingSkillVersion() {
        SkillDefinition original = registry.required("visual-plan", "1.0.0");
        assertThatIllegalArgumentException().isThrownBy(() -> new SkillRegistry(List.of(original, original)));
    }

    @Test
    void validatesSchemaToolsAndBudgetBeforeInvocation() {
        SkillDefinition research = registry.required("research-brief", "1.0.0");
        validator.validateInvocation(research, Map.of("topic", "safe research"), Set.of(ToolId.SEARCH), 1);
        validator.validateOutput(research, Map.of("researchBundle", "bundle-id"));

        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateInvocation(research, Map.of(), Set.of(), 0));
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateInvocation(research,
                Map.of("topic", "safe research", "extra", "not declared"), Set.of(), 0));
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateInvocation(research,
                Map.of("topic", "safe research"), Set.of(ToolId.WEB_READER), 4));
        assertThatIllegalArgumentException().isThrownBy(() -> validator.validateOutput(research, Map.of("unknown", "value")));
    }
}
