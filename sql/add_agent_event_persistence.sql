# P4 E1：统一 Agent Event 事件流。仅新增表，不改变既有 Run/Checkpoint 语义。
use ai_passage_creator;

create table if not exists agent_event_sequence
(
    runId        varchar(64) not null primary key comment '所属运行ID',
    nextSequence bigint      not null comment '下一个可分配事件序号',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP
) comment '按运行分配严格递增事件序号';

create table if not exists agent_event
(
    id           bigint auto_increment primary key,
    runId        varchar(64)  not null,
    sequence     bigint       not null,
    eventType    varchar(64)  not null,
    nodeId       varchar(128) null,
    agentName    varchar(128) null,
    attempt      int          null,
    payload      json         null comment '安全白名单摘要，禁止保存 Prompt、模型正文或密钥',
    createTime   datetime default CURRENT_TIMESTAMP not null,
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    isDelete     tinyint default 0 not null,
    unique key uk_agent_event_run_sequence (runId, sequence),
    index idx_agent_event_run_sequence (runId, sequence)
) comment 'Agent运行追加事件；用于SSE补发与审计回放';
