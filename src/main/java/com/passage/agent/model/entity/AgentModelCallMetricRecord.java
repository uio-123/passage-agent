package com.passage.agent.model.entity;
import com.mybatisflex.annotation.*; import lombok.*; import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor @Table(value="agent_model_call_metric",camelToUnderline=false)
public class AgentModelCallMetricRecord { @Id(keyType=KeyType.Auto) private Long id; private String runId; private String stage; private Integer attempt; private Integer callIndex; private String callType; private Integer retryCount; private String modelName; private Long inputTokens; private Long outputTokens; private Long totalTokens; private Long firstTokenMs; private Long durationMs; private String errorCode; private LocalDateTime createTime; private LocalDateTime updateTime; @Column(isLogicDelete=true) private Integer isDelete; }
