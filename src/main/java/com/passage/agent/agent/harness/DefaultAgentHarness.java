package com.passage.agent.agent.harness;

import com.passage.agent.agent.api.WorkflowExecutionResult;
import com.passage.agent.agent.api.WorkflowRunner;
import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import com.passage.agent.agent.supervisor.HumanDecision;
import com.passage.agent.agent.supervisor.PlanFeedback;
import com.passage.agent.agent.supervisor.PlanReplanner;
import com.passage.agent.agent.supervisor.ReplanResult;
import com.passage.agent.agent.supervisor.SupervisorPlan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.function.Consumer;

/** Harness facade that delegates execution, durable state and plan revision. */
@Service
public class DefaultAgentHarness implements AgentHarness {

    private final WorkflowRunner workflowRunner;
    private final StateManager stateManager;
    private final PlanReplanner planReplanner;

    @Autowired
    public DefaultAgentHarness(
            WorkflowRunner workflowRunner,
            StateManager stateManager,
            PlanReplanner planReplanner) {
        this.workflowRunner = Objects.requireNonNull(workflowRunner, "workflowRunner");
        this.stateManager = Objects.requireNonNull(stateManager, "stateManager");
        this.planReplanner = Objects.requireNonNull(planReplanner, "planReplanner");
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

    @Override
    public ReplanResult replan(SupervisorPlan plan, PlanFeedback feedback) {
        return planReplanner.replan(plan, feedback);
    }

    @Override
    public ReplanResult handleHumanDecision(SupervisorPlan plan, HumanDecision decision) {
        return replan(plan, PlanFeedback.human(decision));
    }
}
