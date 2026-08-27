package com.yupi.template.agent.run;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class AgentRunContractTest {

    @Test
    void keepsRootAndParentIdentityAndAllowsApprovalPauseAndTerminalTransitions() {
        Instant createdAt = Instant.parse("2026-08-27T08:00:00Z");
        AgentRun root = AgentRun.root("run-root", createdAt)
                .transitionTo(AgentRunStatus.RUNNING, createdAt.plusSeconds(1))
                .transitionTo(AgentRunStatus.WAITING_FOR_APPROVAL, createdAt.plusSeconds(2));
        AgentRun child = AgentRun.child("run-child", root, createdAt.plusSeconds(3))
                .transitionTo(AgentRunStatus.RUNNING, createdAt.plusSeconds(4))
                .transitionTo(AgentRunStatus.COMPLETED, createdAt.plusSeconds(5));

        assertThat(root.rootRunId()).isEqualTo("run-root");
        assertThat(child.rootRunId()).isEqualTo("run-root");
        assertThat(child.parentRunId()).isEqualTo("run-root");
        assertThat(child.status().terminal()).isTrue();
    }

    @Test
    void rejectsTransitionsOutOfTerminalRuns() {
        Instant now = Instant.parse("2026-08-27T08:00:00Z");
        AgentRun completed = AgentRun.root("run-1", now)
                .transitionTo(AgentRunStatus.RUNNING, now.plusSeconds(1))
                .transitionTo(AgentRunStatus.COMPLETED, now.plusSeconds(2));

        assertThatIllegalStateException()
                .isThrownBy(() -> completed.transitionTo(AgentRunStatus.RUNNING, now.plusSeconds(3)));
    }
}
