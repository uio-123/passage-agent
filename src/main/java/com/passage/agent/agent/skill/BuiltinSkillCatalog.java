package com.passage.agent.agent.skill;

import com.passage.agent.agent.tool.ToolId;

import java.util.List;
import java.util.Set;

/** Built-in, source-controlled Skill declarations. New versions must use a new version string. */
public final class BuiltinSkillCatalog {
    private BuiltinSkillCatalog() { }

    public static List<SkillDefinition> definitions() {
        return List.of(
                definition("research-brief", Set.of("topic"), Set.of("researchBundle"), Set.of(ToolId.SEARCH, ToolId.WEB_READER), 3,
                        "Every claim is sourced or explicitly unresolved"),
                definition("longform-article", Set.of("outline", "researchBundle"), Set.of("articleMarkdown"), Set.of(), 0,
                        "Output follows the approved outline"),
                definition("fact-check", Set.of("articleMarkdown", "researchBundle"), Set.of("factCheckReport"), Set.of(ToolId.WEB_READER), 2,
                        "Unsupported claims are identified"),
                definition("visual-plan", Set.of("articleMarkdown"), Set.of("imagePlan"), Set.of(), 0,
                        "Each proposed image has a bounded placement and purpose"),
                definition("citation-format", Set.of("articleMarkdown", "researchBundle"), Set.of("citedArticleMarkdown"), Set.of(), 0,
                        "Every emitted citation maps to a source ID")
        );
    }

    private static SkillDefinition definition(String id, Set<String> inputs, Set<String> outputs, Set<ToolId> tools,
                                               int maxToolCalls, String acceptance) {
        return new SkillDefinition(id, "1.0.0", new SkillSchema(inputs, inputs), new SkillSchema(outputs, outputs),
                tools, maxToolCalls, List.of(acceptance));
    }
}
