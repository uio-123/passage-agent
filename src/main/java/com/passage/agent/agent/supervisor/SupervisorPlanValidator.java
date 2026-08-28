package com.passage.agent.agent.supervisor;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Rejects unbounded or unauthorized model plans before they reach routing. */
@Component
public class SupervisorPlanValidator {

    public void validate(SupervisorPlan plan) {
        if (plan.subtasks().size() > plan.maxSubtasks()) {
            throw new IllegalArgumentException("Supervisor plan exceeds its subtask budget");
        }
        Map<String, SubtaskSpec> byId = new HashMap<>();
        for (SubtaskSpec subtask : plan.subtasks()) {
            if (subtask.id() == null || subtask.id().isBlank() || byId.putIfAbsent(subtask.id(), subtask) != null) {
                throw new IllegalArgumentException("Supervisor plan has duplicate or blank subtask id");
            }
            if (!plan.allowedTools().containsAll(subtask.requiredTools())) {
                throw new IllegalArgumentException("Supervisor plan requests an unauthorized tool");
            }
        }
        for (SubtaskSpec subtask : plan.subtasks()) {
            for (String dependency : subtask.dependsOn()) {
                if (!byId.containsKey(dependency) || dependency.equals(subtask.id())) {
                    throw new IllegalArgumentException("Supervisor plan has an invalid dependency");
                }
            }
        }
        for (String id : byId.keySet()) {
            ensureAcyclic(id, byId, new HashSet<>(), new HashSet<>());
        }
    }

    private void ensureAcyclic(String id, Map<String, SubtaskSpec> byId, Set<String> visiting, Set<String> visited) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalArgumentException("Supervisor plan has cyclic dependencies");
        for (String dependency : byId.get(id).dependsOn()) ensureAcyclic(dependency, byId, visiting, visited);
        visiting.remove(id);
        visited.add(id);
    }
}
