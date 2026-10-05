package com.passage.agent.agent.workspace;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalTaskWorkspaceTest {

    @Test
    void writesAndReadsOnlyInsideTheDeclaredArea() {
        Path tempDirectory = newWorkspaceRoot();
        LocalTaskWorkspace workspace = new LocalTaskWorkspace(tempDirectory, "task-1");

        workspace.writeString(WorkspaceArea.CHAPTERS, "chapter-1.md", "draft");

        assertThat(workspace.exists(WorkspaceArea.CHAPTERS, "chapter-1.md")).isTrue();
        assertThat(workspace.readString(WorkspaceArea.CHAPTERS, "chapter-1.md")).isEqualTo("draft");
        assertThat(workspace.resolve(WorkspaceArea.CHAPTERS, "chapter-1.md").startsWith(
                tempDirectory.resolve("task-1").resolve("chapters"))).isTrue();
    }

    @Test
    void rejectsPathTraversalAndAbsolutePaths() {
        Path tempDirectory = newWorkspaceRoot();
        LocalTaskWorkspace workspace = new LocalTaskWorkspace(tempDirectory, "task-1");

        assertThatThrownBy(() -> workspace.resolve(WorkspaceArea.CHAPTERS, "../task-2/secret.md"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes");
        assertThatThrownBy(() -> workspace.resolve(WorkspaceArea.CHAPTERS, tempDirectory.resolve("absolute.md").toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute");
    }

    @Test
    void keepsSeparateTasksIsolated() {
        Path tempDirectory = newWorkspaceRoot();
        LocalTaskWorkspace first = new LocalTaskWorkspace(tempDirectory, "task-1");
        LocalTaskWorkspace second = new LocalTaskWorkspace(tempDirectory, "task-2");
        first.writeString(WorkspaceArea.ARTIFACTS, "result.json", "{}");

        assertThat(second.exists(WorkspaceArea.ARTIFACTS, "result.json")).isFalse();
    }

    private static Path newWorkspaceRoot() {
        Path root = Path.of("target", "test-workspaces", UUID.randomUUID().toString()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            return root;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create test workspace root", exception);
        }
    }
}
