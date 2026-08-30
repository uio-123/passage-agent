package com.passage.agent.agent.workflow;

import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import com.passage.agent.agent.review.QualityGateDecision;
import com.passage.agent.agent.run.AgentRunStatus;
import com.passage.agent.agent.state.WorkflowState;
import com.passage.agent.service.AgentCheckpointService;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Publishes the sole successful P3-to-main-flow recovery boundary. */
@Service
public class P3ContentCheckpointPublisher {
    private final AgentCheckpointService checkpoints;
    private final P3ContentCheckpointCodec codec;
    public P3ContentCheckpointPublisher(AgentCheckpointService checkpoints, P3ContentCheckpointCodec codec) {
        this.checkpoints = checkpoints; this.codec = codec;
    }
    public WorkflowCheckpoint publish(WorkflowState state, long expectedStateVersion, ApprovedOutlineWritingResult result) {
        if (result == null || result.decision().decision() != QualityGateDecision.Decision.ACCEPT) {
            throw new IllegalArgumentException("Only accepted P3 content can create a checkpoint");
        }
        if (state == null || state.draft().selectedTitle() == null) {
            throw new IllegalArgumentException("Approved title is required to publish P3 content");
        }
        String runId = state.run().runId();
        int version = result.latestVersion().version();
        List<String> artifacts = List.of("article-markdown-v" + version, "source-bundle-v" + version, "quality-report-v" + version);
        String checkpointId = UUID.nameUUIDFromBytes((runId + ":content-quality-accepted:" + expectedStateVersion)
                .getBytes(StandardCharsets.UTF_8)).toString();
        P3ContentCheckpointSnapshot snapshot = new P3ContentCheckpointSnapshot(runId, P3ContentCheckpointSnapshot.MODE,
                expectedStateVersion + 1, result.markdown(), version, artifacts, "ACCEPT",
                new P3ContentDeliveryContext(state.draft().selectedTitle().getMainTitle(),
                        state.draft().selectedTitle().getSubTitle(), state.input().style(), state.input().enabledImageMethods()));
        return checkpoints.persistCheckpoint(runId, expectedStateVersion, checkpointId, "content-quality-accepted",
                codec.write(snapshot), AgentRunStatus.WAITING_FOR_APPROVAL);
    }
}
