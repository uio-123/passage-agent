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

/** Persistent representation of the framework-neutral AgentRun contract. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "agent_run", camelToUnderline = false)
public class AgentRunRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String runId;

    private String rootRunId;

    private String parentRunId;

    private String taskId;

    private String status;

    private String currentNode;

    private String checkpointId;

    private String stateSnapshot;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Integer isDelete;
}
