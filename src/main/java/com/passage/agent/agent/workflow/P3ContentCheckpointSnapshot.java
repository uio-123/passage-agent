package com.passage.agent.agent.workflow;

import java.util.List;

/** Deliberately small recovery snapshot; sensitive model and source payloads are excluded. */
public record P3ContentCheckpointSnapshot(
        String runId, String workflowMode, long stateVersion, String markdown,
        int articleVersion, List<String> artifactIds, String qualityDecision,
        P3ContentDeliveryContext deliveryContext
) {
    public static final String MODE = "P3_QUALITY_LOOP";
    public P3ContentCheckpointSnapshot {
        if (runId == null || runId.isBlank() || markdown == null || markdown.isBlank() || stateVersion < 0
                || articleVersion < 1 || !MODE.equals(workflowMode) || !"ACCEPT".equals(qualityDecision)) {
            throw new IllegalArgumentException("Invalid P3 content checkpoint snapshot");
        }
        if (deliveryContext == null) {
            throw new IllegalArgumentException("P3 content delivery context is required");
        }
        artifactIds = artifactIds == null ? List.of() : List.copyOf(artifactIds);
    }
}
