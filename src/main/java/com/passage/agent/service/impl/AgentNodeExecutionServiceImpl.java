package com.passage.agent.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.passage.agent.agent.checkpoint.NodeExecutionOutcome;
import com.passage.agent.agent.checkpoint.NodeExecutionStatus;
import com.passage.agent.agent.event.AgentEventInput;
import com.passage.agent.agent.event.AgentEventPublisher;
import com.passage.agent.agent.event.AgentEventType;
import com.passage.agent.mapper.AgentNodeExecutionMapper;
import com.passage.agent.model.entity.AgentNodeExecutionRecord;
import com.passage.agent.service.AgentNodeExecutionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Database uniqueness is the ownership primitive. A completed record returns
 * its committed result; only the owner in RUNNING state may publish a result.
 */
@Service
public class AgentNodeExecutionServiceImpl extends ServiceImpl<AgentNodeExecutionMapper, AgentNodeExecutionRecord>
        implements AgentNodeExecutionService {
    private final AgentEventPublisher events;
    public AgentNodeExecutionServiceImpl(AgentEventPublisher events) { this.events = events; }

    @Override
    public NodeExecutionOutcome executeOnce(String runId, String nodeId, long stateVersion, Supplier<String> action) {
        Objects.requireNonNull(action, "action");
        ExecutionClaim claim = acquire(runId, nodeId, stateVersion);
        if (claim.reused()) {
            return new NodeExecutionOutcome(claim.executionKey(), true, claim.resultSnapshot());
        }
        events.publish(runId, new AgentEventInput(AgentEventType.NODE_STARTED, nodeId, "workflow", 1,
                java.util.Map.of("stateVersion", Long.toString(stateVersion))));
        try {
            String result = Objects.requireNonNull(action.get(), "node action result");
            commitSuccess(claim.executionKey(), result);
            events.publish(runId, new AgentEventInput(AgentEventType.NODE_COMPLETED, nodeId, "workflow", 1,
                    java.util.Map.of("stateVersion", Long.toString(stateVersion))));
            return new NodeExecutionOutcome(claim.executionKey(), false, result);
        } catch (RuntimeException exception) {
            markFailed(claim.executionKey(), exception.getMessage());
            events.publish(runId, new AgentEventInput(AgentEventType.RUN_FAILED, nodeId, "workflow", 1,
                    java.util.Map.of("errorCode", "NODE_EXECUTION_FAILURE")));
            throw exception;
        }
    }

    @Transactional
    protected ExecutionClaim acquire(String runId, String nodeId, long stateVersion) {
        requireText(runId, "runId");
        requireText(nodeId, "nodeId");
        if (stateVersion < 0) {
            throw new IllegalArgumentException("stateVersion must not be negative");
        }
        String executionKey = executionKey(runId, nodeId, stateVersion);
        AgentNodeExecutionRecord existing = getByExecutionKey(executionKey);
        if (existing == null) {
            try {
                AgentNodeExecutionRecord record = AgentNodeExecutionRecord.builder()
                        .executionKey(executionKey).runId(runId).nodeId(nodeId).stateVersion(stateVersion)
                        .status(NodeExecutionStatus.RUNNING.name()).createTime(LocalDateTime.now()).build();
                if (this.save(record)) {
                    return new ExecutionClaim(executionKey, false, null);
                }
            } catch (DuplicateKeyException ignored) {
                // Another process created the record; read its durable state below.
            }
            existing = getByExecutionKey(executionKey);
        }
        if (existing == null) {
            throw new IllegalStateException("Unable to acquire node execution: " + executionKey);
        }
        NodeExecutionStatus status = NodeExecutionStatus.valueOf(existing.getStatus());
        if (status == NodeExecutionStatus.SUCCEEDED) {
            return new ExecutionClaim(executionKey, true, existing.getResultSnapshot());
        }
        if (status == NodeExecutionStatus.FAILED) {
            AgentNodeExecutionRecord retry = AgentNodeExecutionRecord.builder()
                    .status(NodeExecutionStatus.RUNNING.name()).errorMessage(null).build();
            boolean retried = this.update(retry, QueryWrapper.create()
                    .eq("executionKey", executionKey).eq("status", NodeExecutionStatus.FAILED.name()));
            if (retried) {
                return new ExecutionClaim(executionKey, false, null);
            }
            return acquire(runId, nodeId, stateVersion);
        }
        throw new IllegalStateException("Node execution is already running: " + executionKey);
    }

    @Transactional
    protected void commitSuccess(String executionKey, String resultSnapshot) {
        AgentNodeExecutionRecord success = AgentNodeExecutionRecord.builder()
                .status(NodeExecutionStatus.SUCCEEDED.name()).resultSnapshot(resultSnapshot).errorMessage(null).build();
        boolean committed = this.update(success, QueryWrapper.create()
                .eq("executionKey", executionKey).eq("status", NodeExecutionStatus.RUNNING.name()));
        if (!committed) {
            throw new IllegalStateException("Node execution ownership was lost: " + executionKey);
        }
    }

    @Transactional
    protected void markFailed(String executionKey, String errorMessage) {
        AgentNodeExecutionRecord failed = AgentNodeExecutionRecord.builder()
                .status(NodeExecutionStatus.FAILED.name()).errorMessage(errorMessage).build();
        this.update(failed, QueryWrapper.create()
                .eq("executionKey", executionKey).eq("status", NodeExecutionStatus.RUNNING.name()));
    }

    private AgentNodeExecutionRecord getByExecutionKey(String executionKey) {
        return this.getOne(QueryWrapper.create().eq("executionKey", executionKey));
    }

    private String executionKey(String runId, String nodeId, long stateVersion) {
        return runId + ":" + nodeId + ":" + stateVersion;
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private record ExecutionClaim(String executionKey, boolean reused, String resultSnapshot) {
    }
}
