package com.yupi.template.model.entity;

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

/** Durable node-attempt record keyed by run, node and input state version. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "agent_node_execution", camelToUnderline = false)
public class AgentNodeExecutionRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;
    private String executionKey;
    private String runId;
    private String nodeId;
    private Long stateVersion;
    private String status;
    private String resultSnapshot;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Integer isDelete;
}
