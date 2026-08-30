package com.passage.agent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.passage.agent.agent.event.AgentEvent;
import com.passage.agent.agent.event.AgentEventInput;
import com.passage.agent.agent.event.AgentEventPayloadSanitizer;
import com.passage.agent.agent.event.AgentEventType;
import com.passage.agent.mapper.AgentEventMapper;
import com.passage.agent.model.entity.AgentEventRecord;
import com.passage.agent.service.AgentEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class AgentEventServiceImpl extends ServiceImpl<AgentEventMapper, AgentEventRecord> implements AgentEventService {
    private static final int MAX_PAGE_SIZE = 200;
    private final ObjectMapper objectMapper;
    public AgentEventServiceImpl(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override @Transactional
    public AgentEvent append(String runId, AgentEventInput input) {
        if (runId == null || runId.isBlank() || input == null || input.eventType() == null) throw new IllegalArgumentException("Invalid agent event");
        mapper().advanceSequence(runId);
        long sequence = mapper().currentAllocatedSequence(runId);
        Map<String, String> payload = AgentEventPayloadSanitizer.sanitize(input.payload());
        LocalDateTime now = LocalDateTime.now();
        AgentEventRecord record = AgentEventRecord.builder().runId(runId).sequence(sequence)
                .eventType(input.eventType().name()).nodeId(input.nodeId()).agentName(input.agentName())
                .attempt(input.attempt()).payload(toJson(payload)).createTime(now).updateTime(now).build();
        if (!save(record)) throw new IllegalStateException("Unable to persist agent event");
        return toDomain(record, payload);
    }

    @Override
    public List<AgentEvent> findAfter(String runId, long afterSequence, int limit) {
        if (afterSequence < 0) throw new IllegalArgumentException("afterSequence must not be negative");
        int bounded = Math.max(1, Math.min(MAX_PAGE_SIZE, limit));
        return list(QueryWrapper.create().eq("runId", runId).gt("sequence", afterSequence).orderBy("sequence", true).limit(bounded))
                .stream().map(record -> toDomain(record, readPayload(record.getPayload()))).toList();
    }

    private AgentEventMapper mapper() { return getMapper(); }
    private String toJson(Map<String, String> value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException("Unable to serialize event payload", e); } }
    @SuppressWarnings("unchecked") private Map<String, String> readPayload(String value) { try { return value == null ? Map.of() : objectMapper.readValue(value, Map.class); } catch (JsonProcessingException e) { throw new IllegalStateException("Invalid persisted event payload", e); } }
    private AgentEvent toDomain(AgentEventRecord record, Map<String, String> payload) { return new AgentEvent(record.getRunId(), record.getSequence(), AgentEventType.valueOf(record.getEventType()), record.getNodeId(), record.getAgentName(), record.getAttempt(), payload, record.getCreateTime().atZone(ZoneId.systemDefault()).toInstant()); }
}
