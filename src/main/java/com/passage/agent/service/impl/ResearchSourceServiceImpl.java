package com.passage.agent.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.passage.agent.agent.research.ResearchSource;
import com.passage.agent.mapper.ResearchSourceMapper;
import com.passage.agent.model.entity.ResearchSourceRecord;
import com.passage.agent.service.ResearchSourceService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class ResearchSourceServiceImpl extends ServiceImpl<ResearchSourceMapper, ResearchSourceRecord> implements ResearchSourceService {
    @Override public ResearchSourceRecord saveOrReuse(String runId, ResearchSource source) {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId must not be blank");
        ResearchSourceRecord existing = find(runId, source);
        if (existing != null) return existing;
        ResearchSourceRecord record = new ResearchSourceRecord();
        record.setRunId(runId); record.setCanonicalUrl(source.canonicalUrl()); record.setTitle(source.title());
        record.setPublisher(source.publisher()); record.setFetchedAt(LocalDateTime.ofInstant(source.fetchedAt(), ZoneId.systemDefault()));
        record.setContentHash(source.contentHash()); record.setSummary(source.summary()); record.setSearchQuery(source.query());
        record.setStatus(source.status().name()); record.setCreateTime(LocalDateTime.now());
        try { save(record); } catch (DuplicateKeyException ignored) { return find(runId, source); }
        return record;
    }
    @Override public List<ResearchSourceRecord> listByRunId(String runId) {
        return list(QueryWrapper.create().eq("runId", runId).orderBy("id", true));
    }
    private ResearchSourceRecord find(String runId, ResearchSource s) {
        return getOne(QueryWrapper.create().eq("runId", runId).eq("canonicalUrl", s.canonicalUrl()).eq("contentHash", s.contentHash()));
    }
}
