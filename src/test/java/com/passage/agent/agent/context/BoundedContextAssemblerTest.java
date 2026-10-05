package com.passage.agent.agent.context;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedContextAssemblerTest {

    private final BoundedContextAssembler assembler = new BoundedContextAssembler();

    @Test
    void filtersContextToTheRoleProfile() {
        ContextAssemblyRequest request = new ContextAssemblyRequest("run-h3", AgentContext.Role.WRITER,
                new AgentContext.Budget(100, 0), List.of(
                AgentContext.Item.of(AgentContext.Key.USER_REQUEST, "write an article", AgentContext.Priority.REQUIRED),
                AgentContext.Item.of(AgentContext.Key.CURRENT_CHAPTER, "chapter one", AgentContext.Priority.REQUIRED),
                AgentContext.Item.of(AgentContext.Key.RESEARCH_GOAL, "internal planner goal", AgentContext.Priority.HIGH),
                AgentContext.Item.of(AgentContext.Key.CURRENT_DRAFT, "reviewer-only draft", AgentContext.Priority.HIGH)));

        AgentContext context = assembler.assemble(request);

        assertThat(context.items()).extracting(AgentContext.Item::key)
                .containsExactlyInAnyOrder(AgentContext.Key.USER_REQUEST, AgentContext.Key.CURRENT_CHAPTER);
    }

    @Test
    void appliesTheBudgetBeforeOptionalContext() {
        AgentContext.Item required = AgentContext.Item.of(
                AgentContext.Key.USER_REQUEST, "request", AgentContext.Priority.REQUIRED);
        AgentContext.Item chapter = AgentContext.Item.of(
                AgentContext.Key.CURRENT_CHAPTER, "chapter", AgentContext.Priority.REQUIRED);
        AgentContext.Item optional = AgentContext.Item.of(
                AgentContext.Key.OUTLINE, "x".repeat(200), AgentContext.Priority.LOW);
        ContextAssemblyRequest request = new ContextAssemblyRequest("run-h3", AgentContext.Role.WRITER,
                new AgentContext.Budget(10, 0), List.of(optional, chapter, required));

        AgentContext context = assembler.assemble(request);

        assertThat(context.items()).extracting(AgentContext.Item::key)
                .containsExactlyInAnyOrder(AgentContext.Key.USER_REQUEST, AgentContext.Key.CURRENT_CHAPTER);
        assertThat(context.estimatedTokens()).isLessThanOrEqualTo(10);
    }

    @Test
    void rejectsMissingRequiredContext() {
        ContextAssemblyRequest request = new ContextAssemblyRequest("run-h3", AgentContext.Role.REVIEWER,
                new AgentContext.Budget(100, 0), List.of(
                AgentContext.Item.of(AgentContext.Key.QUALITY_RUBRIC, "rubric", AgentContext.Priority.NORMAL)));

        assertThatThrownBy(() -> assembler.assemble(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required context");
    }
}
