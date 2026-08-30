package com.passage.agent.service;
import com.passage.agent.agent.research.ResearchSource;
import com.passage.agent.model.entity.ResearchSourceRecord;
import java.util.List;
public interface ResearchSourceService {
    ResearchSourceRecord saveOrReuse(String runId, ResearchSource source);
    List<ResearchSourceRecord> listByRunId(String runId);
}
