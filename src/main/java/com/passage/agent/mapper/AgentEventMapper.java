package com.passage.agent.mapper;

import com.mybatisflex.core.BaseMapper;
import com.passage.agent.model.entity.AgentEventRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AgentEventMapper extends BaseMapper<AgentEventRecord> {
    @Insert("insert into agent_event_sequence (runId, nextSequence) values (#{runId}, 1) " +
            "on duplicate key update nextSequence = last_insert_id(nextSequence + 1)")
    void advanceSequence(@Param("runId") String runId);

    /** Reads the durable counter instead of connection-scoped LAST_INSERT_ID(), which may be stale after a first insert. */
    @Select("select nextSequence from agent_event_sequence where runId = #{runId}")
    Long currentAllocatedSequence(@Param("runId") String runId);
}
