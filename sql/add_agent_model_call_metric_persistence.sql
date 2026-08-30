use ai_passage_creator;
create table if not exists agent_model_call_metric
(
 id bigint auto_increment primary key,
 runId varchar(64) not null,
 stage varchar(64) not null,
 attempt int not null,
 callIndex int not null,
 callType varchar(32) not null,
 retryCount int not null default 0,
 modelName varchar(256) null,
 inputTokens bigint null,
 outputTokens bigint null,
 totalTokens bigint null,
 firstTokenMs bigint null,
 durationMs bigint null,
 errorCode varchar(64) null,
 createTime datetime default CURRENT_TIMESTAMP not null,
 updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
 isDelete tinyint default 0 not null,
 unique key uk_model_metric_run_stage_attempt_call (runId,stage,attempt,callIndex),
 index idx_model_metric_run (runId,createTime)
) comment '可信模型调用测量；Token未知时存NULL';

# Existing deployments may have run an earlier draft of this migration.
set @retry_column_exists := (select count(*) from information_schema.columns where table_schema = database() and table_name = 'agent_model_call_metric' and column_name = 'retryCount');
set @retry_column_sql := if(@retry_column_exists = 0, 'alter table agent_model_call_metric add column retryCount int not null default 0 after callType', 'select 1');
prepare retry_column_statement from @retry_column_sql;
execute retry_column_statement;
deallocate prepare retry_column_statement;
