package com.passage.agent.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Persistent checkpoint; status changes are always guarded by the current value. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "agent_checkpoint", camelToUnderline = false)
public class AgentCheckpointRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;
    private String checkpointId;
    private String runId;
    private String nodeId;
    private Long stateVersion;
    private String stateSnapshot;
    private String status;
    private LocalDateTime claimedAt;
    private LocalDateTime consumedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Integer isDelete;
}
