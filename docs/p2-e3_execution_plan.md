# P2 E3：来源与 Tool 审计持久化执行计划

> 状态：已完成
> 日期：2026-08-29
> 依据：[p2_execution_plan.md](p2_execution_plan.md)、[p2-e2_execution_plan.md](p2-e2_execution_plan.md) 与现有 P1 持久化模型。

## 1. 目标与边界

E3 将 E1/E2 的可追溯来源和脱敏 Tool 审计持久化，使后续 Research Agent 可以把来源绑定到 child run，并让故障、重试和成功读取可查询。它不接入真实 Search/Research Agent/Supervisor 主图，不把网页正文或密钥写入数据库，也不改变现有 checkpoint 或图片副作用幂等模型。

## 2. 数据模型与约束

新增幂等迁移 `sql/add_agent_research_persistence.sql`：

| 表 | 关键列与约束 |
|---|---|
| `research_source` | `runId`、canonical URL、标题、发布者、抓取时间、内容哈希、受限摘要、检索查询、来源状态；唯一键 `(runId, canonicalUrl, contentHash)`，避免同一读取结果重复成为有效来源 |
| `tool_call_audit` | `runId`、Tool、脱敏 target、状态、错误分类、耗时、重试、响应字节、关联 node execution（可空）、创建时间；不存 query、Authorization、Cookie、正文或密钥 |

实体、Mapper 与 Service 使用项目已有 MyBatis-Flex 模式。Service 是 E3 唯一写入口：来源写入采用唯一键冲突后的查询复用；审计每次调用尝试保留一条记录，不作为幂等副作用结果。

## 3. 实施步骤

1. 新增 SQL、实体和 Mapper，并将迁移按 `08_` 顺序挂入 Docker Compose；README 补充已有数据库的手动升级命令。
2. 新增 `ResearchSourceService`：保存/查询 run 来源、按业务唯一键复用，校验摘要不超过 `ResearchSource.MAX_SUMMARY_LENGTH`。
3. 新增 `ToolCallAuditService` 及持久化 `ToolAuditSink` 适配；Gateway 仍可接受内存 sink，E4 再通过 Spring 绑定接入持久化 sink。
4. 扩展现有 Testcontainers 集成测试，在同一 MySQL 容器加载 E3 迁移并覆盖来源去重、审计脱敏字段与不同失败结果的可查询性。
5. 更新 P2/E3 文档、开发记录、Compose 与 README，执行默认和 `persistence-integration` 回归。

## 4. 验收与回滚

- 同一 run 的同 canonical URL + 内容哈希重复保存返回同一来源记录；哈希变化或不同 run 产生独立来源。
- 审计记录可按 run 查询，保留 Tool、错误码、耗时、重试和大小；目标不含查询字符串，任何字段不出现 Token/正文。
- 默认 `mvn test` 不启动 Docker；`mvn test -Ppersistence-integration` 在 Docker 可用时实际覆盖 E3 表和约束。
- 回滚仅停止 E3 Service/Sink 绑定；新表和已有数据保留，P1 Workflow、checkpoint 与图片 Tool 不受影响。

## 5. 实施结果

- 已新增 `research_source` 与 `tool_call_audit` 迁移、MyBatis-Flex 实体/Mapper/Service，并接入 Compose 初始化及 README 的手动升级说明。
- `ResearchSourceService` 以 `runId + canonicalUrl + contentHash` 查询复用来源；`ToolCallAuditService` 仅持久化 E2 的脱敏审计字段。
- Testcontainers MySQL 实际执行 `AgentCheckpointPersistenceIntegrationTest` 共 7 项，0 失败、0 错误、0 跳过，覆盖同 Run 来源复用、跨 Run 独立来源和审计脱敏字段；Docker 引擎已实际连接，未使用 Mock 替代。
