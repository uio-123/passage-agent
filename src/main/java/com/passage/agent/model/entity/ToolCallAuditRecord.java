package com.passage.agent.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Table(value = "tool_call_audit", camelToUnderline = false)
public class ToolCallAuditRecord {
    @Id(keyType = KeyType.Auto) private Long id;
    private String runId; private String toolId; private String target; private Boolean successful; private String errorCode;
    private Long elapsedMillis; private Integer retryCount; private Integer responseBytes; private String nodeExecutionKey;
    private LocalDateTime createTime;
}
