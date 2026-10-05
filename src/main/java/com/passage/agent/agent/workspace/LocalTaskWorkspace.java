package com.passage.agent.agent.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.regex.Pattern;

/** Controlled local-directory implementation; it is not wired as a production Spring bean. */
public final class LocalTaskWorkspace implements TaskWorkspace {

    private static final Pattern SAFE_TASK_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    private final String taskId;
    private final Path root;

    public LocalTaskWorkspace(Path baseRoot, String taskId) {
        this.taskId = requireTaskId(taskId);
        Path normalizedBase = Objects.requireNonNull(baseRoot, "baseRoot").toAbsolutePath().normalize();
        this.root = normalizedBase.resolve(this.taskId).normalize();
        if (!root.startsWith(normalizedBase)) {
            throw new IllegalArgumentException("task workspace escapes its base root");
        }
    }

    @Override
    public String taskId() {
        return taskId;
    }

    @Override
    public Path resolve(WorkspaceArea area, String relativePath) {
        Objects.requireNonNull(area, "area");
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }
        Path relative;
        try {
            relative = Path.of(relativePath);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("relativePath is invalid", exception);
        }
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("absolute workspace paths are not allowed");
        }
        Path areaRoot = root.resolve(area.directory()).normalize();
        Path resolved = areaRoot.resolve(relative).normalize();
        if (!resolved.startsWith(areaRoot)) {
            throw new IllegalArgumentException("workspace path escapes its area");
        }
        return resolved;
    }

    @Override
    public void writeString(WorkspaceArea area, String relativePath, String content) {
        Objects.requireNonNull(content, "content");
        Path target = resolve(area, relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write task workspace file", exception);
        }
    }

    @Override
    public String readString(WorkspaceArea area, String relativePath) {
        try {
            return Files.readString(resolve(area, relativePath), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read task workspace file", exception);
        }
    }

    @Override
    public boolean exists(WorkspaceArea area, String relativePath) {
        return Files.exists(resolve(area, relativePath));
    }

    private static String requireTaskId(String taskId) {
        if (taskId == null || !SAFE_TASK_ID.matcher(taskId).matches()) {
            throw new IllegalArgumentException("taskId contains unsupported characters");
        }
        return taskId;
    }
}
