package com.passage.agent.agent.workspace;

/** Fixed directories available inside one task workspace. */
public enum WorkspaceArea {
    RESEARCH("research"),
    OUTLINE("outline"),
    CHAPTERS("chapters"),
    IMAGES("images"),
    ARTIFACTS("artifacts");

    private final String directory;

    WorkspaceArea(String directory) {
        this.directory = directory;
    }

    public String directory() {
        return directory;
    }
}
