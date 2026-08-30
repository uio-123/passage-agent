# P2 E3：研究来源与脱敏 Tool 审计持久化增量（已有数据库手动执行）
use ai_passage_creator;

create table if not exists research_source
(
    id           bigint auto_increment primary key,
    runId        varchar(64)   not null,
    canonicalUrl varchar(2048) not null,
    title        varchar(512)  not null,
    publisher    varchar(512)  null,
    fetchedAt    datetime      not null,
    contentHash  varchar(128)  not null,
    summary      text          not null,
    searchQuery  varchar(1024) not null,
    status       varchar(32)   not null,
    createTime   datetime default CURRENT_TIMESTAMP not null,
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    isDelete     tinyint default 0 not null,
    unique key uk_run_url_hash (runId, canonicalUrl(255), contentHash),
    index idx_runId (runId)
) comment 'P2 研究来源；不保存完整网页正文';

create table if not exists tool_call_audit
(
    id               bigint auto_increment primary key,
    runId            varchar(64)  not null,
    toolId           varchar(64)  not null,
    target           varchar(512) not null comment 'origin和路径哈希，不含查询参数',
    successful       tinyint      not null,
    errorCode        varchar(64) null,
    elapsedMillis    bigint      not null,
    retryCount       int         not null,
    responseBytes    int         not null,
    nodeExecutionKey varchar(192) null,
    createTime       datetime default CURRENT_TIMESTAMP not null,
    isDelete         tinyint default 0 not null,
    index idx_runId (runId),
    index idx_nodeExecutionKey (nodeExecutionKey)
) comment 'P2 Tool 脱敏审计；不保存密钥、请求头或正文';
