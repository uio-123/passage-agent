# P1：可恢复工作流持久化增量（已有数据库手动执行）
# 本脚本依赖 sql/add_agent_run_tables.sql 已执行。

use ai_passage_creator;

# 补齐旧部署缺失的版本列；新部署已由 add_agent_run_tables.sql 创建该列。
set @state_version_exists := (
    select count(*)
    from information_schema.columns
    where table_schema = database()
      and table_name = 'agent_run'
      and column_name = 'stateVersion'
);
set @state_version_sql := if(
    @state_version_exists = 0,
    'alter table agent_run add column stateVersion bigint default 0 not null comment ''已持久化状态版本，用于乐观推进'' after checkpointId',
    'select 1'
);
prepare state_version_statement from @state_version_sql;
execute state_version_statement;
deallocate prepare state_version_statement;

create table if not exists agent_checkpoint
(
    id            bigint auto_increment comment 'id' primary key,
    checkpointId  varchar(128) not null comment 'checkpoint标识',
    runId         varchar(64)  not null comment '所属运行ID',
    nodeId        varchar(128) not null comment '成功完成的节点',
    stateVersion  bigint       not null comment '该快照对应的状态版本',
    stateSnapshot json         not null comment '可恢复的工作流状态',
    status        varchar(32)  not null comment 'READY/CLAIMED/CONSUMED/CANCELLED',
    claimedAt     datetime     null comment '恢复者取得推进权的时间',
    consumedAt    datetime     null comment '已成功消费的时间',
    createTime    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint  default 0                 not null comment '逻辑删除',
    unique key uk_checkpointId (checkpointId),
    unique key uk_run_stateVersion (runId, stateVersion),
    index idx_run_status (runId, status)
) comment 'Agent运行checkpoint；恢复推进权由状态条件更新取得';

create table if not exists agent_node_execution
(
    id              bigint auto_increment comment 'id' primary key,
    executionKey    varchar(192) not null comment 'runId:nodeId:stateVersion幂等键',
    runId           varchar(64)  not null comment '所属运行ID',
    nodeId          varchar(128) not null comment '节点标识',
    stateVersion    bigint       not null comment '执行前状态版本',
    status          varchar(32)  not null comment 'PENDING/RUNNING/SUCCEEDED/FAILED',
    resultSnapshot  json         null comment '已提交节点结果',
    errorMessage    text         null comment '失败信息',
    createTime      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint  default 0                 not null comment '逻辑删除',
    unique key uk_executionKey (executionKey),
    index idx_run_node (runId, nodeId)
) comment 'Agent节点执行和幂等提交记录；外部副作用关联在P1 E4接入';
