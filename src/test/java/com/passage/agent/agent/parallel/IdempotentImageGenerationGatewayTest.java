package com.passage.agent.agent.parallel;

import com.passage.agent.agent.checkpoint.NodeExecutionOutcome;
import com.passage.agent.agent.tools.ImageGenerationTool;
import com.passage.agent.model.entity.AgentRunRecord;
import com.passage.agent.service.AgentNodeExecutionService;
import com.passage.agent.service.AgentRunService;
import com.passage.agent.utils.GsonUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentImageGenerationGatewayTest {

    @Test
    void reusesTheCommittedImageWhenCheckpointPublicationMustBeRetried() {
        AgentRunService runs = mock(AgentRunService.class);
        AgentNodeExecutionService executions = mock(AgentNodeExecutionService.class);
        when(runs.getByRunId("run-1")).thenReturn(AgentRunRecord.builder().runId("run-1").stateVersion(4L).build());
        ImageGenerationTool.ImageGenerationResult committed = successful("https://images.example/once");
        when(executions.executeOnce(eq("run-1"), eq("image:cover"), eq(4L), any()))
                .thenReturn(new NodeExecutionOutcome("run-1:image:cover:4", true, GsonUtils.toJson(committed)));
        AtomicInteger calls = new AtomicInteger();
        IdempotentImageGenerationGateway gateway = new IdempotentImageGenerationGateway(executions, runs);

        var result = gateway.execute("run-1", "image:cover", () -> {
            calls.incrementAndGet();
            return successful("https://images.example/duplicate");
        });

        assertThat(result.getUrl()).isEqualTo("https://images.example/once");
        assertThat(calls).hasValue(0);
        verify(executions).executeOnce(eq("run-1"), eq("image:cover"), eq(4L), any());
    }

    private ImageGenerationTool.ImageGenerationResult successful(String url) {
        ImageGenerationTool.ImageGenerationResult result = new ImageGenerationTool.ImageGenerationResult();
        result.setSuccess(true);
        result.setUrl(url);
        return result;
    }
}
