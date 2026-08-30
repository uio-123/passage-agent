package com.passage.agent.service.impl;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.passage.agent.agent.context.ObservabilityContextSnapshot;
import com.passage.agent.agent.context.ContextSnapshotSanitizer;
import com.passage.agent.mapper.AgentContextSnapshotMapper;
import com.passage.agent.model.entity.AgentContextSnapshotRecord;
import com.passage.agent.service.AgentContextSnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;
@Service public class AgentContextSnapshotServiceImpl extends ServiceImpl<AgentContextSnapshotMapper,AgentContextSnapshotRecord> implements AgentContextSnapshotService {
 @Override @Transactional public ObservabilityContextSnapshot append(String runId,String stage,String summary,long before,long after){
  if(runId==null||runId.isBlank()||stage==null||stage.isBlank()||before<0||after<0) throw new IllegalArgumentException("Invalid context snapshot");
  getMapper().advance(runId); long sequence=getMapper().allocated(runId); LocalDateTime now=LocalDateTime.now();
  var record=AgentContextSnapshotRecord.builder().runId(runId).sequence(sequence).stage(stage).summary(ContextSnapshotSanitizer.sanitize(summary)).tokenBefore(before).tokenAfter(after).createTime(now).updateTime(now).build();
  if(!save(record)) throw new IllegalStateException("Unable to persist context snapshot"); return map(record);
 }
 @Override public List<ObservabilityContextSnapshot> list(String runId){ return list(QueryWrapper.create().eq("runId",runId).orderBy("sequence",true)).stream().map(this::map).toList(); }
 private ObservabilityContextSnapshot map(AgentContextSnapshotRecord r){ return new ObservabilityContextSnapshot(r.getRunId(),r.getSequence(),r.getStage(),r.getSummary(),r.getTokenBefore(),r.getTokenAfter(),r.getCreateTime().atZone(ZoneId.systemDefault()).toInstant()); }
}
