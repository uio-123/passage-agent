package com.passage.agent.agent.harness;

import com.passage.agent.agent.api.WorkflowExecutionResult;
import com.passage.agent.agent.api.WorkflowRunner;
import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.function.Consumer;

/** H1 facade that delegates every operation to an existing implementation. */
@Service
public class DefaultAgentHarness implements AgentHarness {

    private final WorkflowRunner workflowRunner;
    private final StateManager stateManager;

    public DefaultAgentHarness(WorkflowRunner workflowRunner, StateManager stateManager) {
        this.workflowRunner = Objects.requireNonNull(workflowRunner, "workflowRunner");
        this.stateManager = Objects.requireNonNull(stateManager, "stateManager");
    }

    @Override
    public WorkflowExecutionResult run(AgentTask task, Consumer<String> streamHandler) {
        Objects.requireNonNull(task, "task");
        return switch (task.type()) {
            case GENERATE_TITLES -> workflowRunner.generateTitles(task.state(), streamHandler);
            case GENERATE_OUTLINE -> workflowRunner.generateOutline(task.state(), streamHandler);
            case GENERATE_CONTENT -> workflowRunner.generateContent(task.state(), streamHandler);
        };
    }

    @Override
    public void resume(String checkpointId, Consumer<WorkflowCheckpoint> nextNode) {
        stateManager.resumeCheckpoint(checkpointId, nextNode);
    }

    @Override
    public boolean cancel(String runId) {
        return stateManager.cancelRun(runId);
    }
}
