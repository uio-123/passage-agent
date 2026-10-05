package com.passage.agent.agent.harness;

import com.passage.agent.agent.api.WorkflowExecutionResult;
import com.passage.agent.agent.checkpoint.WorkflowCheckpoint;
import com.passage.agent.agent.supervisor.HumanDecision;
import com.passage.agent.agent.supervisor.PlanFeedback;
import com.passage.agent.agent.supervisor.ReplanResult;
import com.passage.agent.agent.supervisor.SupervisorPlan;

import java.util.function.Consumer;

/**
 * Stable application entry for one agent task. H1 unified workflow, recovery
 * and cancellation boundaries; H2 adds a deterministic plan-revision boundary.
 */
public interface AgentHarness {

    WorkflowExecutionResult run(AgentTask task, Consumer<String> streamHandler);

    void resume(String checkpointId, Consumer<WorkflowCheckpoint> nextNode);

    boolean cancel(String runId);

    ReplanResult replan(SupervisorPlan plan, PlanFeedback feedback);

    ReplanResult handleHumanDecision(SupervisorPlan plan, HumanDecision decision);
}
