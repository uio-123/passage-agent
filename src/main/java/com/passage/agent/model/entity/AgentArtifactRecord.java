package com.passage.agent.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table(value = "agent_artifact", camelToUnderline = false)
public class AgentArtifactRecord {
    @Id(keyType = KeyType.Auto) private Long id;
    private String runId;
    private Integer articleVersion;
    private String artifactId;
    private String artifactType;
    private String location;
    private String sha256;
    private LocalDateTime createTime;
}
