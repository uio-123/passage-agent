package com.passage.agent.mapper;
import com.mybatisflex.core.BaseMapper;
import com.passage.agent.model.entity.AgentContextSnapshotRecord;
import org.apache.ibatis.annotations.*;
public interface AgentContextSnapshotMapper extends BaseMapper<AgentContextSnapshotRecord> {
 @Insert("insert into agent_context_snapshot_sequence (runId,nextSequence) values (#{runId},1) on duplicate key update nextSequence=last_insert_id(nextSequence+1)") void advance(@Param("runId") String runId);
 @Select("select nextSequence from agent_context_snapshot_sequence where runId = #{runId}") Long allocated(@Param("runId") String runId);
}
