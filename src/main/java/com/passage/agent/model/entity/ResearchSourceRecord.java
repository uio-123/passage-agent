package com.passage.agent.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Table(value = "research_source", camelToUnderline = false)
public class ResearchSourceRecord {
    @Id(keyType = KeyType.Auto) private Long id;
    private String runId; private String canonicalUrl; private String title; private String publisher;
    private LocalDateTime fetchedAt; private String contentHash; private String summary; private String searchQuery;
    private String status; private LocalDateTime createTime; private LocalDateTime updateTime;
}
