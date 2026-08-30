package com.passage.agent.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table(value = "agent_article_version", camelToUnderline = false)
public class AgentArticleVersionRecord {
    @Id(keyType = KeyType.Auto) private Long id;
    private String runId;
    private Integer version;
    private Integer parentVersion;
    private String changeReason;
    private String draftsSnapshot;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
