package com.passage.agent.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.passage.agent.agent.metrics.ModelCallMeasurement;
import com.passage.agent.mapper.AgentModelCallMetricMapper;
import com.passage.agent.model.entity.AgentModelCallMetricRecord;
import com.passage.agent.service.AgentModelCallMetricService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AgentModelCallMetricServiceImpl
        extends ServiceImpl<AgentModelCallMetricMapper, AgentModelCallMetricRecord>
        implements AgentModelCallMetricService {

    @Override
    public void record(String runId, String stage, int attempt, int callIndex, ModelCallMeasurement measurement) {
        save(AgentModelCallMetricRecord.builder()
                .runId(runId)
                .stage(stage)
                .attempt(attempt)
                .callIndex(callIndex)
                .callType(measurement.callType())
                .retryCount(measurement.retryCount())
                .modelName(measurement.modelName())
                .inputTokens(measurement.inputTokens())
                .outputTokens(measurement.outputTokens())
                .totalTokens(measurement.totalTokens())
                .firstTokenMs(measurement.firstTokenMs())
                .durationMs(measurement.durationMs())
                .errorCode(measurement.errorCode())
                .createTime(LocalDateTime.now())
                .build());
    }

    @Override
    public List<AgentModelCallMetricRecord> listByRunId(String runId) {
        return list(QueryWrapper.create().eq("runId", runId).orderBy("id", true));
    }
}
