package com.passage.agent.model.entity;
import com.mybatisflex.annotation.*;
import lombok.*;
import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table(value="agent_context_snapshot", camelToUnderline=false)
public class AgentContextSnapshotRecord {
 @Id(keyType=KeyType.Auto) private Long id; private String runId; private Long sequence; private String stage; private String summary; private Long tokenBefore; private Long tokenAfter; private LocalDateTime createTime; private LocalDateTime updateTime; @Column(isLogicDelete=true) private Integer isDelete;
}
