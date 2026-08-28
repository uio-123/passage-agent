package com.passage.agent.agent.run;

import com.passage.agent.agent.artifact.ArtifactManifest;
import com.passage.agent.agent.artifact.ArtifactType;
import com.passage.agent.agent.context.ContextSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AgentRunArtifactsContractTest {

    @Test
    void bindsSubtasksSnapshotsAndArtifactsToTheSameRun() {
        String runId = "run-1";
        AgentSubtask subtask = new AgentSubtask(
                "subtask-1", runId, 0, "撰写第一章节", List.of("search"), 1_000, "包含两个事实来源");
        ContextSnapshot snapshot = new ContextSnapshot(
                runId, "已确认文章主题", List.of("主题为 Agent"), List.of("先写标题"), List.of("生成大纲"), Instant.now());
        ArtifactManifest manifest = new ArtifactManifest(runId, List.of(), Instant.now()).add(
                new ArtifactManifest.Artifact("article-1", ArtifactType.ARTICLE_MARKDOWN, "article://task-1", "abc"));

        assertThat(subtask.parentRunId()).isEqualTo(runId);
        assertThat(snapshot.runId()).isEqualTo(runId);
        assertThat(manifest.runId()).isEqualTo(runId);
        assertThat(manifest.artifacts()).extracting(ArtifactManifest.Artifact::type)
                .containsExactly(ArtifactType.ARTICLE_MARKDOWN);
    }

    @Test
    void rejectsSubtasksWithoutBoundedExecutionRules() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AgentSubtask(
                "subtask-1", "run-1", 0, "写作", List.of(), 0, "完成"));
    }
}
