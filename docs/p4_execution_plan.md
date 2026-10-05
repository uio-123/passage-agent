# P4：实时可观测 UI 与运行治理执行计划

状态：`STAGING`；核心事件、运行详情、Artifact/Context 面板与模型调用测量已实现，默认关闭，未完成项移交 Harness H3/H5。
前置条件：P1 的 Run/checkpoint 持久化与 P2/P3 的 Tool、质量、Artifact 契约已可用；P3 默认 feature flag 继续关闭。  
目标：在不暴露模型原始流、Prompt、密钥、网页正文或框架事件格式的前提下，使一次 Agent Run 的状态、路由、节点、产物、质量与成本可回放、可展示、可排障。

## 阶段与交付顺序

| 阶段 | 先行文档 | 实施范围 | 验收 |
|---|---|---|---|
| E1 | `p4-e1_event_replay_execution.md` | 项目自有事件模型、append-only 事件表、SSE 序号/补发 | 同一 run 事件严格递增；`Last-Event-ID` 只补发缺口；敏感字段不落库/不出流 |
| E2 | `p4-e2_run_detail_ui_execution.md` | Run 查询投影、DAG/父子链路/时间线 UI | 刷新或重连后 UI 可由快照和事件恢复；不依赖后端日志 |
| E3 | `p4-e3_artifact_panel_execution.md` | Artifact/version/read-only 下载入口与 UI | 文章、来源、图片、质量报告均显示版本、状态与安全下载入口 |
| E4 | `p4-e4_context_snapshot_execution.md` | Context Snapshot、脱敏摘要、Token 压缩前后统计 | UI 仅显示安全摘要；关键状态、用户决定及未完成项可恢复 |
| E5 | `p4-e5_metrics_governance_execution.md` | 节点/模型/Tool 指标、成本估算、管理端聚合和告警视图 | 可查看 P50/P95、失败率、慢节点、重试与失败分类；成本为估算值并注明来源 |
| E6 | `p4-e6_e2e_acceptance_execution.md` | API/UI 回归、演示脚本、文档收口 | 完整演示不查看日志即可说明输入、输出、耗时、路由和返工原因 |

每个 E 的专属文档必须先定义数据边界、API、迁移、测试矩阵、回滚方案和非目标；经该文档写入仓库后才可修改对应代码。不得在较早阶段预建后续页面、指标或表字段。

## 跨阶段约束

- 统一事件是项目 DTO，前端不可依赖 Spring AI 或 StateGraph 的事件格式。
- 事件持久化为 append-only；事件 `sequence` 在单个 `runId` 内唯一、严格递增。重试可追加事件，绝不改写既有事件。
- SSE 认证、文章授权和访问边界沿用现有服务；不能因调试接口泄露其他用户 run。
- 存储 payload 采用最小安全摘要：禁止写入 API Key、认证头、Cookie、完整 Prompt、网页正文、完整模型流或用户敏感字段。对失败原因使用受控错误码和脱敏信息。
- P4 只观测、不改变 P1–P3 的状态机、重试、质量门禁或 Artifact 追加语义。每次接入以旁路发布失败保护主流程。
- 新迁移遵循项目 SQL 初始化流程，并为 MySQL Testcontainers 增加真实验证；默认单元测试保持离线。

## 当前风险与回滚

事件发布器必须是 best-effort：数据库事件记录或 SSE client 故障不能回滚业务节点已提交的状态。E1 可通过 `article.agent.observability.events-enabled=false` 关闭新事件写入/推送，旧 SSE 保持可用；后续阶段分别提供只读页面或功能开关回退，绝不删除历史事件。
