package com.passage.agent.agent.supervisor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupervisorPlanValidatorTest {

    private final SupervisorPlanValidator validator = new SupervisorPlanValidator();

    @Test
    void acceptsAPlanWithinItsDeclaredBudgetAndToolPermissions() {
        assertThatCode(() -> validator.validate(new SupervisorPlan(false, 2, 2, Set.of("search"), List.of(
                new SubtaskSpec(0, "research", "collect", List.of(), Set.of("search")),
                new SubtaskSpec(1, "write", "draft", List.of("research"), Set.of())
        )))).doesNotThrowAnyException();
    }

    @Test
    void rejectsBudgetDependencyAndToolPermissionViolations() {
        assertThatThrownBy(() -> validator.validate(new SupervisorPlan(false, 1, 1, Set.of(), List.of(
                new SubtaskSpec(0, "one", "one"), new SubtaskSpec(1, "two", "two")
        )))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("budget");

        assertThatThrownBy(() -> validator.validate(new SupervisorPlan(false, 1, 2, Set.of(), List.of(
                new SubtaskSpec(0, "one", "one", List.of("missing"), Set.of())
        )))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dependency");

        assertThatThrownBy(() -> validator.validate(new SupervisorPlan(false, 1, 1, Set.of(), List.of(
                new SubtaskSpec(0, "one", "one", List.of(), Set.of("search"))
        )))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unauthorized");
    }
}
