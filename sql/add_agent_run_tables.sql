# Agent Run 持久化基础
# @author AI Passage Creator

use ai_passage_creator;

create table if not exists agent_run
(
    id            bigint auto_increment comment 'id' primary key,
    runId         varchar(64)  not null comment '运行ID',
    rootRunId     varchar(64)  not null comment '根运行ID',
    parentRunId   varchar(64)  null comment '父运行ID，根任务为空',
    taskId        varchar(64)  not null comment '关联文章任务ID',
    status        varchar(32)  not null comment 'PENDING/RUNNING/WAITING_FOR_APPROVAL/PAUSED/COMPLETED/FAILED/CANCELLED',
    currentNode   varchar(128) null comment '最后确认的工作流节点',
    checkpointId  varchar(128) null comment '框架checkpoint标识',
    stateSnapshot json         null comment '持久化工作流状态快照',
    errorMessage  text         null comment '失败信息',
    createTime    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint  default 0                 not null comment '逻辑删除',
    unique key uk_runId (runId),
    index idx_taskId (taskId),
    index idx_rootRunId (rootRunId),
    index idx_parentRunId (parentRunId),
    index idx_status (status)
) comment 'Agent主任务和子任务运行记录';
