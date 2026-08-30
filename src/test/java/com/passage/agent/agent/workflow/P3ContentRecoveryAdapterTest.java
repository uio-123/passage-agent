package com.passage.agent.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passage.agent.agent.checkpoint.CheckpointStatus;
import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import com.passage.agent.agent.fixture.ArticleWorkflowFixture;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class P3ContentRecoveryAdapterTest {
    private final P3ContentCheckpointCodec codec = new P3ContentCheckpointCodec(new ObjectMapper());
    @Test void restoresMarkdownWithoutAP3ExecutionDependency() {
        String snapshot = codec.write(new P3ContentCheckpointSnapshot("run", P3ContentCheckpointSnapshot.MODE, 1,
                "restored markdown", 1, List.of("article-markdown-v1"), "ACCEPT",
                new P3ContentDeliveryContext("Title", null, "guide", List.of())));
        WorkflowCheckpoint checkpoint = new WorkflowCheckpoint("cp", "run", "content-quality-accepted", 1, snapshot,
                CheckpointStatus.CLAIMED, Instant.EPOCH, Instant.EPOCH);
        var restored = new P3ContentRecoveryAdapter(codec).restore(checkpoint, ArticleWorkflowFixture.workflowState("run", "task"));
        assertThat(restored.draft().content()).isEqualTo("restored markdown");
    }
    @Test void rejectsAnUnrelatedCheckpoint() {
        WorkflowCheckpoint checkpoint = new WorkflowCheckpoint("cp", "run", "titles", 1, "{}", CheckpointStatus.CLAIMED, Instant.EPOCH, Instant.EPOCH);
        assertThatIllegalArgumentException().isThrownBy(() -> new P3ContentRecoveryAdapter(codec).restore(checkpoint,
                ArticleWorkflowFixture.workflowState("run", "task")));
    }
}
