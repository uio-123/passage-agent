use ai_passage_creator;
create table if not exists agent_context_snapshot_sequence
(
    runId varchar(64) not null primary key,
    nextSequence bigint not null,
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP
) comment '按运行分配上下文摘要序号';
create table if not exists agent_context_snapshot
(
    id bigint auto_increment primary key,
    runId varchar(64) not null,
    sequence bigint not null,
    stage varchar(64) not null,
    summary varchar(2048) not null,
    tokenBefore bigint not null default 0,
    tokenAfter bigint not null default 0,
    createTime datetime default CURRENT_TIMESTAMP not null,
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    isDelete tinyint default 0 not null,
    unique key uk_context_snapshot_run_sequence (runId, sequence),
    index idx_context_snapshot_run_sequence (runId, sequence)
) comment '安全、追加式Agent上下文摘要；不保存Prompt或完整状态';
