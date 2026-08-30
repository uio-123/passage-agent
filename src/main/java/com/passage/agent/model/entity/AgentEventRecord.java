package com.passage.agent.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table(value = "agent_event", camelToUnderline = false)
public class AgentEventRecord {
    @Id(keyType = KeyType.Auto) private Long id;
    private String runId;
    private Long sequence;
    private String eventType;
    private String nodeId;
    private String agentName;
    private Integer attempt;
    private String payload;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @Column(isLogicDelete = true) private Integer isDelete;
}
