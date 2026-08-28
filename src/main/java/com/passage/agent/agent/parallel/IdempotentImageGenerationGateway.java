package com.passage.agent.agent.parallel;

import com.passage.agent.agent.tools.ImageGenerationTool;
import com.passage.agent.model.entity.AgentRunRecord;
import com.passage.agent.service.AgentNodeExecutionService;
import com.passage.agent.service.AgentRunService;
import com.passage.agent.utils.GsonUtils;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Supplier;

/** Persists a successful image side effect before its graph checkpoint advances. */
@Component
public class IdempotentImageGenerationGateway {
    private final AgentNodeExecutionService nodeExecutions;
    private final AgentRunService runs;

    public IdempotentImageGenerationGateway(AgentNodeExecutionService nodeExecutions, AgentRunService runs) {
        this.nodeExecutions = nodeExecutions;
        this.runs = runs;
    }

    public ImageGenerationTool.ImageGenerationResult execute(
            String runId, String imageNodeId, Supplier<ImageGenerationTool.ImageGenerationResult> action) {
        AgentRunRecord run = runs.getByRunId(runId);
        if (run == null) {
            return action.get(); // Legacy requests without an AgentRun stay compatible.
        }
        long stateVersion = run.getStateVersion() == null ? 0L : run.getStateVersion();
        try {
            var outcome = nodeExecutions.executeOnce(runId, imageNodeId, stateVersion, () -> {
                ImageGenerationTool.ImageGenerationResult result = action.get();
                if (!result.isSuccess()) throw new ImageGenerationFailed(result);
                return GsonUtils.toJson(result);
            });
            return GsonUtils.fromJson(outcome.resultSnapshot(), ImageGenerationTool.ImageGenerationResult.class);
        } catch (ImageGenerationFailed failed) {
            return failed.result;
        }
    }

    private static final class ImageGenerationFailed extends RuntimeException {
        private final ImageGenerationTool.ImageGenerationResult result;

        private ImageGenerationFailed(ImageGenerationTool.ImageGenerationResult result) {
            this.result = Objects.requireNonNull(result, "result");
        }
    }
}
