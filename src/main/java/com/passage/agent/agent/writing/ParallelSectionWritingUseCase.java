package com.passage.agent.agent.writing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passage.agent.agent.checkpoint.NodeExecutionOutcome;
import com.passage.agent.agent.research.ResearchBundle;
import com.passage.agent.agent.supervisor.SubtaskSpec;
import com.passage.agent.agent.supervisor.SupervisorPlan;
import com.passage.agent.agent.supervisor.SupervisorScheduler;
import com.passage.agent.model.entity.AgentRunRecord;
import com.passage.agent.service.AgentNodeExecutionService;
import com.passage.agent.service.AgentRunService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** P3 E2 bridge: bounded section fan-out reuses child runs and durable node snapshots. */
@Service
public class ParallelSectionWritingUseCase {
    private static final String NODE_ID = "section-writer";

    private final SupervisorScheduler scheduler;
    private final AgentRunService agentRunService;
    private final AgentNodeExecutionService nodeExecutions;
    private final ObjectMapper objectMapper;
    private final SectionDraftValidator draftValidator = new SectionDraftValidator();
    private final SectionFanIn fanIn = new SectionFanIn();

    public ParallelSectionWritingUseCase(SupervisorScheduler scheduler, AgentRunService agentRunService,
                                         AgentNodeExecutionService nodeExecutions, ObjectMapper objectMapper) {
        this.scheduler = scheduler;
        this.agentRunService = agentRunService;
        this.nodeExecutions = nodeExecutions;
        this.objectMapper = objectMapper;
    }

    public SectionWritingResult execute(String parentRunId, long stateVersion, int maxConcurrency,
                                        ResearchBundle research, List<SectionTask> tasks, SectionWriter writer) {
        if (parentRunId == null || parentRunId.isBlank() || stateVersion < 0 || maxConcurrency < 1
                || research == null || writer == null) {
            throw new IllegalArgumentException("Invalid section writing execution input");
        }
        List<SectionTask> safeTasks = tasks == null ? List.of() : List.copyOf(tasks);
        if (safeTasks.isEmpty()) return new SectionWritingResult(List.of(), Map.of());
        Map<String, SectionTask> taskById = new LinkedHashMap<>();
        for (SectionTask task : safeTasks) {
            if (taskById.putIfAbsent(task.id(), task) != null) throw new IllegalArgumentException("SectionTask ids must be unique");
        }
        if (taskById.values().stream().map(SectionTask::sectionIndex).distinct().count() != taskById.size()) {
            throw new IllegalArgumentException("SectionTask indexes must be unique");
        }
        SupervisorPlan plan = new SupervisorPlan(false, maxConcurrency, safeTasks.size(), java.util.Set.of(),
                safeTasks.stream().map(task -> new SubtaskSpec(task.sectionIndex(), task.id(), task.instruction())).toList());
        Map<String, String> childRunIds = new java.util.concurrent.ConcurrentHashMap<>();
        List<SectionDraft> drafts = scheduler.executeWriters(plan, subtask -> {
            SectionTask task = taskById.get(subtask.id());
            String childRunId = ensureChildRun(parentRunId, task.id());
            childRunIds.put(task.id(), childRunId);
            NodeExecutionOutcome outcome = nodeExecutions.executeOnce(childRunId, NODE_ID, stateVersion, () -> {
                SectionDraft draft = writer.write(new SectionWriterRequest(task, research));
                draftValidator.validate(new SectionWriterRequest(task, research), draft);
                return serialize(draft);
            });
            return outcome.resultSnapshot();
        }).stream().map(result -> deserialize(result.content())).toList();
        return new SectionWritingResult(fanIn.merge(drafts), Map.copyOf(childRunIds));
    }

    private String ensureChildRun(String parentRunId, String sectionId) {
        String childRunId = UUID.nameUUIDFromBytes((parentRunId + ":writer:" + sectionId).getBytes(StandardCharsets.UTF_8)).toString();
        AgentRunRecord existing = agentRunService.getByRunId(childRunId);
        if (existing == null) agentRunService.createChildRun(childRunId, parentRunId);
        return childRunId;
    }

    private String serialize(SectionDraft draft) {
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to persist SectionDraft snapshot", exception);
        }
    }

    private SectionDraft deserialize(String snapshot) {
        try {
            return objectMapper.readValue(snapshot, SectionDraft.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored SectionDraft snapshot is invalid", exception);
        }
    }

    public record SectionWritingResult(List<SectionDraft> drafts, Map<String, String> childRunIds) {
        public SectionWritingResult {
            drafts = List.copyOf(drafts);
            childRunIds = Map.copyOf(childRunIds);
        }
    }
}
