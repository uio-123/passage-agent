package com.passage.agent.agent.workspace;

import java.nio.file.Path;

/** Isolated file boundary for one task; Artifact records remain the canonical manifest. */
public interface TaskWorkspace {

    String taskId();

    Path resolve(WorkspaceArea area, String relativePath);

    void writeString(WorkspaceArea area, String relativePath, String content);

    String readString(WorkspaceArea area, String relativePath);

    boolean exists(WorkspaceArea area, String relativePath);
}
