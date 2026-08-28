package com.yupi.template.agent.checkpoint;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class WorkflowCheckpointContractTest {

    @Test
    void allowsOneWayClaimAndConsumptionButNeverReopensAConsumedCheckpoint() {
        Instant createdAt = Instant.parse("2026-08-28T08:00:00Z");
        WorkflowCheckpoint checkpoint = new WorkflowCheckpoint(
                "checkpoint-1", "run-1", "outline", 3, "{\"taskId\":\"task-1\"}",
                CheckpointStatus.READY, createdAt, createdAt);

        WorkflowCheckpoint consumed = checkpoint
                .transitionTo(CheckpointStatus.CLAIMED, createdAt.plusSeconds(1))
                .transitionTo(CheckpointStatus.CONSUMED, createdAt.plusSeconds(2));

        assertThat(consumed.status()).isEqualTo(CheckpointStatus.CONSUMED);
        assertThatIllegalStateException().isThrownBy(() ->
                consumed.transitionTo(CheckpointStatus.CLAIMED, createdAt.plusSeconds(3)));
    }
}
