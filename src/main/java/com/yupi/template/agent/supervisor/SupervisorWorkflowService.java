package com.yupi.template.agent.supervisor;

import com.yupi.template.model.entity.AgentRunRecord;
import com.yupi.template.service.AgentRunService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Business boundary for a validated Supervisor plan. Research is deliberately
 * an injected placeholder in P1; real search tools remain a P2 concern.
 */
@Service
public class SupervisorWorkflowService {
    private final SupervisorPlanValidator validator;
    private final SupervisorScheduler scheduler;
    private final AgentRunService agentRunService;

    public SupervisorWorkflowService(SupervisorPlanValidator validator, SupervisorScheduler scheduler,
                                     AgentRunService agentRunService) {
        this.validator = validator;
        this.scheduler = scheduler;
        this.agentRunService = agentRunService;
    }

    public SupervisorExecutionResult execute(String parentRunId, SupervisorPlan plan, Runnable research,
                                             Function<SubtaskSpec, String> writer) {
        validator.validate(plan);
        ExecutionRoute route = scheduler.initialRoute(plan);
        List<ChildExecution> children = new ArrayList<>();
        if (route == ExecutionRoute.RESEARCH) {
            String childRunId = ensureChildRun(parentRunId, "research");
            research.run();
            children.add(new ChildExecution(childRunId, "research", null));
        }

        Map<String, SubtaskSpec> remaining = new LinkedHashMap<>();
        plan.subtasks().stream().sorted(java.util.Comparator.comparingInt(SubtaskSpec::sectionIndex))
                .forEach(subtask -> remaining.put(subtask.id(), subtask));
        List<String> completed = new ArrayList<>();
        while (!remaining.isEmpty()) {
            List<SubtaskSpec> ready = remaining.values().stream()
                    .filter(subtask -> completed.containsAll(subtask.dependsOn())).toList();
            if (ready.isEmpty()) throw new IllegalStateException("Validated supervisor plan cannot be scheduled");
            SupervisorPlan wave = new SupervisorPlan(false, plan.maxConcurrency(), ready.size(),
                    plan.allowedTools(), ready);
            Map<String, ChildExecution> waveChildren = new ConcurrentHashMap<>();
            List<SupervisorScheduler.SubtaskResult> results = scheduler.executeWriters(wave, subtask -> {
                String childRunId = ensureChildRun(parentRunId, "writer:" + subtask.id());
                String content = writer.apply(subtask);
                waveChildren.put(subtask.id(), new ChildExecution(childRunId, subtask.id(), content));
                return content;
            });
            results.forEach(result -> {
                remaining.remove(result.subtaskId());
                completed.add(result.subtaskId());
                children.add(waveChildren.get(result.subtaskId()));
            });
        }
        return new SupervisorExecutionResult(route, List.copyOf(children));
    }

    private String ensureChildRun(String parentRunId, String nodeKey) {
        String childRunId = UUID.nameUUIDFromBytes((parentRunId + ':' + nodeKey)
                .getBytes(StandardCharsets.UTF_8)).toString();
        AgentRunRecord existing = agentRunService.getByRunId(childRunId);
        if (existing == null) agentRunService.createChildRun(childRunId, parentRunId);
        return childRunId;
    }

    public record SupervisorExecutionResult(ExecutionRoute route, List<ChildExecution> children) { }
    public record ChildExecution(String childRunId, String nodeId, String content) { }
}
