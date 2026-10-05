# Agent Harness 化改造执行计划

> 状态：H0 `DONE`；H1–H5 未启动
> 日期：2026-10-05
> 基线标签：`baseline-before-harness`
> 目标：在不破坏现有主链路的前提下，把已经存在的 Run、Checkpoint、Planner、Tool、Reviewer、Event 等能力收口为可验证的 Agent Harness，并用小规模真实任务证明收益后再扩大范围。

## 1. 总体判断

项目已经具备较多 Harness 零件，但这些能力仍分散在 Workflow、Controller、Service 和若干阶段模块中：

- `WorkflowRunner` 是当前统一执行入口，但还不是完整 Harness。
- `AgentRunService`、`AgentCheckpointService` 和 `WorkflowRecoveryService` 已承担持久状态职责，不应再复制一套平行的 StateManager。
- `SupervisorPlan`、`SubtaskSpec` 和 `SupervisorWorkflowService` 已存在，但缺少由 Reviewer Feedback 驱动的版本化 Replan。
- `ToolRegistry`、`ToolPolicyGateway` 和 Web Reader 已存在，但缺少统一 Tool Executor、ToolContext 与任务 Workspace 边界。
- `ContextSnapshot` 当前主要服务和观测，不能直接等同于 Agent 的输入上下文。
- P3 质量闭环和 P4 可观测能力已经实现，但默认关闭，属于 `STAGING`。

因此本轮改造的核心原则是：

```text
已有 Service 继续作为实现
Harness 负责统一入口和边界
Planner 生成或调整计划
StateGraph 执行计划
StateManager 只管理持久状态
```

不得为了让架构图更完整而创建第二套执行引擎、状态机或 Agent 调度器。

## 2. 状态口径与当前矩阵

统一使用以下四个状态：

| 状态 | 含义 |
|---|---|
| `DONE` | 目标范围内的实现、验证和文档已经收口 |
| `STAGING` | 代码已实现并通过验证，但默认关闭、部分入口未生产化或仍需真实任务验收 |
| `IN_PROGRESS` | 仍缺少原目标中的关键能力，尚不能作为完整阶段交付 |
| `DRAFT` | 只有协议、样本或脚手架，正式结果与发布门槛尚未满足 |

当前阶段矩阵：

| 阶段 | 状态 | 事实边界 |
|---|---|---|
| P0-A | `DONE` | Java 21、LiteLLM、Docker 与配置基线已收口 |
| P0-U | `DONE` | Spring AI 1.1.2.2 兼容性验证与版本冻结已完成 |
| P0-B | `DONE` | Run、Typed State、Runner 与核心契约骨架已落地；统一单图的遗留目标由 Harness H1/H2 继承 |
| P1 | `STAGING` | Run、Checkpoint、恢复、取消和节点幂等已实现；完整 Supervisor/HITL 客户端闭环仍有缺口 |
| P1.5 | `DONE` | 项目身份和命名空间迁移已完成 |
| P2 | `IN_PROGRESS` | Policy Gateway、Web Reader、来源持久化和 Skill Registry 已完成；真实 Search Tool 与 Skill Resolver 尚未交付 |
| P3 | `STAGING` | 并行写作、双 Reviewer、局部 Revision、Artifact 和主流程继续入口已实现；`article.agent.quality-loop.enabled=false` |
| P4 | `STAGING` | Event/Replay、Run Detail、Artifact/Context 面板和模型调用测量已实现；默认关闭，受控下载、完整 DAG 和上下文压缩仍缺失 |
| P5 | `DRAFT` | 评测、CI、压测和 Demo 工具已实现；30 条数据未裁决，正式评测与性能基线未接受 |
| Harness H0 | `DONE` | 本文档、基线矩阵、状态统一和基线提交 |

`docs/plan.md` 中各阶段的历史复选框保留为实施记录，不再作为当前状态的唯一事实来源；本矩阵和各阶段执行文档必须保持一致。

## 3. Harness 基线

### 3.1 默认运行配置

| 配置 | 默认值 | 结果 |
|---|---:|---|
| `article.agent.orchestrator.enabled` | `true` | 默认使用当前三阶段 StateGraph 编排 |
| `article.agent.quality-loop.enabled` | `false` | 正文继续走旧 ContentGenerator 图，不进入 P3 章节质量闭环 |
| `article.agent.observability.events-enabled` | `false` | 不写入或推送新的可回放 Agent Event |
| `article.agent.observability.context-snapshots-enabled` | `false` | 不生成 P4 Context Snapshot |

`demo` Profile 会显式开启 Agent Event，但仍关闭 Context Snapshot，并且只允许确定性场景。

### 3.2 当前默认主链路

```text
Article API
  -> ArticleWorkflowRunner
  -> ArticleAgentOrchestrator
  -> 标题
  -> 用户确认
  -> 大纲
  -> 用户确认
  -> 正文
  -> 配图分析
  -> 并行配图
  -> 图文合成
```

P3 质量闭环开启后才改为：

```text
Approved Outline
  -> P3 Section Writers
  -> Fact Checker + Style Reviewer
  -> Quality Gate
  -> 局部 Revision / 最多两轮
  -> content-quality continue
  -> Image Analyzer -> Parallel Image Generator -> Merger
```

### 3.3 状态与持久化

| 层 | 当前结构 |
|---|---|
| Typed Workflow State | `WorkflowState`，包含 Run、ArticleInput、ArticleDraft 和 ArticleArtifacts |
| Legacy Graph State | `ArticleState` 与 `ArticleWorkflowKeys` 过渡映射 |
| Run | `agent_run`，保存 root/parent、状态、当前节点、stateVersion、checkpointId 和状态快照 |
| Checkpoint | `agent_checkpoint`，状态为 READY/CLAIMED/CONSUMED/CANCELLED，并受 run + stateVersion 唯一约束 |
| 节点幂等 | `agent_node_execution`，以 `runId:nodeId:stateVersion` 作为执行键 |
| 事件 | `agent_event_sequence` + `agent_event` |
| 上下文观测 | `agent_context_snapshot_sequence` + `agent_context_snapshot` |
| 模型调用 | `agent_model_call_metric`，缺失 usage 保持 NULL |
| 研究来源 | `research_source` + `tool_call_audit` |
| 版本与交付物 | `agent_article_version` + `agent_artifact` |

### 3.4 基线验证

| 验证层 | 2026-10-05 结果 |
|---|---|
| L0 评测、压测工具与 Compose 静态检查 | 14 项 Node 测试通过；数据集契约通过；两套 Compose 配置通过 |
| 后端默认离线回归 | 141 项、0 失败、0 错误、0 跳过 |
| 前端 | TypeScript 检查与生产构建通过 |
| MySQL Testcontainers | MySQL 8.0 真实容器 14 项通过，0 失败、0 错误、0 跳过 |
| Docker Demo 冒烟 | `PASS`：9 个安全事件、3 个 Artifact、重复请求复用且无新增副作用 |

基线提交、Tag 和最终验证以仓库中的 `baseline-before-harness` 标签为准。

本次 Demo 使用本机已有的 `2026-08-31` 后端/前端镜像启动，业务代码自该轮 P5 构建后未再修改；重新构建镜像时遇到 Docker Hub 基础镜像元数据解析失败，属于当前外部镜像源问题，不影响上述离线、Testcontainers 和 Demo 行为验证。后续 Harness 代码开始前，应恢复一次可信的镜像构建链路。

## 4. 修订后的执行路线

### H1：Harness Facade 与 StateManager

- 新增 `AgentHarness`，委托现有 `WorkflowRunner`、运行查询、恢复和取消能力。
- 新增 `StateManager` 统一门面，内部复用 `AgentRunService`、`AgentCheckpointService` 和 `WorkflowRecoveryService`。
- 不改变现有 API、SSE、数据库语义和默认路径。
- 验收：旧路径行为不变；新旧入口契约测试通过；不得出现双写状态。

### H2：Planner、Reviewer Feedback 与 Replan

- 扩展现有 `SupervisorPlan` / `SubtaskSpec`，增加计划版本和 Replan 原因。
- Reviewer 只输出结构化问题和建议动作，不能直接决定重写、研究或配图。
- 第一阶段由代码规则决定继续、局部重写或补研究；LLM Planner 继续放在 Feature Flag 后。
- HITL 的 `MODIFY` / `REJECT` 进入状态更新和 Replan，而不是只恢复原节点。
- 验收：普通、返工、人工修改和拒绝四类路径可重复测试。

### H3：Context 与 Tool Runtime

- 新增 Agent 级 Context Assembly、选择和 Token 上限；第一阶段不做自动压缩。
- 统一现有 Tool Registry、Policy 和 Audit 的执行边界，新增 ToolContext 与 ToolExecutor。
- 定义 `TaskWorkspace` 端口；本地目录实现仅用于受控环境，Artifact 持久化记录仍是最终事实来源。
- 验收：不同 Agent 只获得职责所需上下文；越权 Tool 和跨任务 Workspace 访问被拒绝。

### H4：五任务 Staging 对比

- 使用真实模型比对旧 Workflow 与 Harness Workflow。
- 固定覆盖普通创作、长任务、Reviewer 返工、人工修改和失败恢复。
- 记录任务成功率、质量、人工修改次数、耗时、Token、Tool 调用、重试和恢复成功率。
- 验收：至少 5/5 完成任务；无重复副作用；形成 Harness 是否带来净收益的书面结论。

### H5：按证据决定后续范围

只有在 H4 证明有效后，才逐项决定：

- Memory Manager 与 Memory Retrieval。
- Context Compression。
- P3 默认开关切换。
- 正式 P5 的 30 条数据、三变体和性能基线。
- 是否引入 Docker Sandbox。

## 5. 明确不做

- 不继续增加无明确职责边界的 Agent。
- 不在 H4 前引入 Vector DB、长期记忆或复杂压缩。
- 不在 H4 前把 P3 默认开关改为 `true`。
- 不立即实现 Docker Sandbox。
- 不为了简历数字降低评测门槛或修改数据集。
- 不让 Agent 直接访问数据库、外部 API、任意文件或 Workflow State。
