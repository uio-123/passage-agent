# P3 E5：文章版本链与 Artifact Manifest 持久化增量（已有数据库手动执行）
use ai_passage_creator;

create table if not exists agent_article_version
(
    id             bigint auto_increment primary key,
    runId          varchar(64) not null,
    version        int         not null,
    parentVersion  int         null,
    changeReason   varchar(1024) not null,
    draftsSnapshot json        not null comment '稳定归并后的章节草稿；不保存模型原始上下文',
    createTime     datetime default CURRENT_TIMESTAMP not null,
    updateTime     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    isDelete       tinyint default 0 not null,
    unique key uk_run_version (runId, version),
    index idx_runId (runId)
) comment 'P3 不可变文章版本链；只允许追加';

create table if not exists agent_artifact
(
    id             bigint auto_increment primary key,
    runId          varchar(64) not null,
    articleVersion int         not null,
    artifactId     varchar(128) not null,
    artifactType   varchar(64) not null,
    location       varchar(2048) not null,
    sha256         char(64)    not null,
    createTime     datetime default CURRENT_TIMESTAMP not null,
    isDelete       tinyint default 0 not null,
    unique key uk_run_version_artifact (runId, articleVersion, artifactId),
    index idx_run_version (runId, articleVersion)
) comment 'P3 Artifact Manifest 条目；逻辑地址与哈希，不保存二进制文件';
