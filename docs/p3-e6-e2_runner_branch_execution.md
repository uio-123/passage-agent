# P3 E6.2：Runner Feature-Flag 分支执行清单

> 状态：`DONE`；Runner 分支已接入，质量开关默认仍为 `false`
> 前置：[p3-e6_stategraph_migration_plan.md](p3-e6_stategraph_migration_plan.md)、`P3ContentWorkflowAdapter`、默认关闭的 `article.agent.quality-loop.enabled`。

## 目标

在不改变默认旧正文路径的前提下，为 `ArticleWorkflowRunner.generateContent` 增加 P3 质量闭环分支。该步骤只负责正文回填和错误映射；图片、SSE/checkpoint 的主流程交接留给 E6.3/E6.4。

## 实施清单

1. 在 Runner 注入 `AgentConfig`、`ApprovedOutlineWritingRequestFactory` 和 `P3ContentWorkflowAdapter`，保留现有公开构造器用于旧测试。
2. `quality-loop.enabled=false` 时保持现有 `graphExecutor.executeContent` 行为逐字不变。
3. 开启时从已审批 `WorkflowState` 创建 typed request。首版使用 `stateVersion=0`、无研究 Bundle；真实 checkpoint version 与 ResearchBundle 接入不在本小步伪造。
4. P3 返回 `ACCEPT` 时仅回填 `WorkflowState.draft.content`，并返回 `CONTENT_QUALITY_ACCEPTED`；不调用旧 `executeContent`，不在本步调用图片节点或直接标记完成。
5. Gate 拒绝或 P3 执行失败映射为新的可恢复 `WorkflowErrorCode`，不返回成功结果。
6. 测试：flag 关闭走旧图；flag 开启成功回填 Markdown 且不走旧图；拒绝/失败不回填。默认回归通过后再进入 E6.3。

## 回滚

将 `article.agent.quality-loop.enabled` 保持/恢复为 `false`；无数据库迁移、无 HTTP 变更、无图片调用。已由 P3 adapter 写入的版本记录保留审计，不删除。

## 2026-08-30 Testcontainers Spring 绑定修复

真实 Testcontainers 首次启动暴露出 Spring 绑定遗漏：`ArticleWorkflowRunner` 的生产构造器要求注入 `ApprovedOutlineWritingRequestFactory`，但该 Factory 尚未注册为 Bean，导致整个 ApplicationContext 无法加载。修复仅为将该无状态 Factory 注册为 Spring component；不改变 P3 请求映射、feature flag 或旧路径。
