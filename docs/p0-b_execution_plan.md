# P0-B 可执行开发计划

> 状态：已校验，执行中（仅覆盖 P0-B）
> 更新日期：2026-08-27
> 依据：[plan.md](plan.md)、[development_log.md](development_log.md)、[README.md](../README.md) 与当前代码。

## 1. 执行范围与不变量

本文件只覆盖当前 P0-B 剩余工作。P1 内容只作为交接门槛说明，不构成当前可执行任务。每项开发都必须先满足以下不变量：

- 保持 Java 21、Spring AI Alibaba `1.1.2.2` 和 Spring AI `1.1.2` 冻结基线，不在功能开发中升级依赖。
- 对外文章 API、既有 SSE 消息类型、标题/大纲人工确认流程和 `article` / `agent_log` 表保持兼容。
- 业务层只依赖 `WorkflowRunner`、typed state、项目自有结果/错误 DTO；StateGraph 类型只允许留在 `agent` 图适配层。
- 每项代码改动须附带无网络的测试；完成后运行 `mvn test`。Windows 首次增量编译出现已知资源关闭问题时，仅可无源码变更重跑，并把最终结果记录到开发日志。
- 已有数据库在启用 Agent Run 功能前必须执行 `sql/add_agent_run_tables.sql`；不得把迁移执行假定为自动完成。

## 2. 已完成基线

| 能力 | 证据 | 状态 |
|---|---|---|
| 框架版本与 LiteLLM 兼容性 | `framework-compatibility.md`、契约/冒烟测试 | 完成并冻结 |
| typed workflow、运行状态和交付物契约 | `agent/state`、`agent/run`、`agent/artifact` | 完成 |
| 根 Run 创建及部署迁移 | `AgentRunService`、`sql/add_agent_run_tables.sql`、Compose 初始化 | 完成 |
| 业务执行边界 | `WorkflowRunner`、`ArticleWorkflowRunner`、`ArticleAsyncService` | 完成 |
| 结果/错误与状态键收敛 | `WorkflowExecutionResult`、`WorkflowError`、`ArticleWorkflowKeys` | 完成 |
| 回归门禁 | 最近一次 `mvn test`：22 tests, 0 failures, 0 errors | 通过 |

## 3. P0-B 顺序执行清单

### E1：建立可复用的固定测试数据基座（已完成）

- 目标：用合成创作任务和确定性 Fake Model 响应替代散落在测试中的临时构造。
- 修改范围：新增 `src/test/.../agent/fixture/`；只重构测试代码，不改生产行为。
- 验收：至少覆盖标题、大纲、正文、空配图和图片部分失败；所有 Fixture 无密钥、无真实用户正文、无公网依赖。
- 回滚：删除新增 Fixture，现有独立测试仍可运行。
- 实际结果：`ArticleWorkflowFixture` 已集中固定文章输入、typed state、确定性 Fake Model 响应以及“单图失败、其余成功”的图片任务；三阶段业务回归、Runner 与图片并行契约已接入。网页抓取回放与来源样本依赖 P2 Research，保留为后续 Fixture 扩展而非伪造数据。

### E2：记录 P0-B 可比较运行基线（下一项）

- 目标：定义并采集本地可重复的阶段耗时、模型调用次数、失败类别；不得把 Fake 环境数据表述为线上性能。
- 修改范围：项目自有 metrics DTO/collector 与 Fake 测试断言；不新增数据库表、不做前端展示。
- 验收：一次固定三阶段工作流产生每阶段耗时、调用次数和结果状态；失败路径产生 `WorkflowErrorCode`；测试不依赖真实模型。
- 回滚：移除 collector 接入，不影响工作流状态或 SSE。

### E3：统一图迁移设计与最小实现

- 目标：将三个已预编译图的拓扑收敛为一个 `agent/graph` 适配器，保留标题/大纲审批的阶段边界。
- 前置：E1、E2 完成；先增加拓扑与阶段转换测试，再迁移生产调用。
- 修改范围：仅图适配层、`ArticleWorkflowRunner` 与契约测试；文章 Controller 和 SSE 协议不改。
- 验收：同一 typed state 在一次图定义中可完成标题、等待确认、大纲、等待确认、正文配图；不跨越未确认标题/大纲；现有 22 项以上测试全部通过。
- 明确限制：P0-B 只交付内存/进程内的暂停边界。持久化 checkpoint、重启恢复、重复 Tool 副作用控制归 P1，不得提前标记完成。
- 回滚：保留三个现有阶段图与 `WorkflowRunner` 过渡实现，通过开关恢复旧图路径。

### E4：P0-B 收口验证与 P1 交接

- 目标：补足普通、流式、结构化、图片 Tool 与图执行的最小契约，并核对文档。
- 验收：测试数据基座、基线指标、统一图契约、旧 SSE 回归均通过；README、plan、开发日志与代码一致。
- 交接：达到后才生成并校验独立的 P1 执行文档；该文档再定义 Supervisor、条件路由、持久化 checkpoint、恢复与幂等的具体工作项。

## 4. 逐项执行门禁

每个 E1–E4 完成前必须检查：

1. `git diff --check` 无空白错误。
2. `mvn test` 最终通过；记录测试数量、失败数、错误数。
3. `plan.md` 只更新实际完成的项，`development_log.md` 记录设计取舍、验证和已知限制。
4. 若涉及运行方式、数据表或部署步骤，则同步 README / Compose / SQL。
5. 若需要真实数据库、Docker、LiteLLM 或破坏性迁移，先停在门禁处，说明所需环境或授权；默认不将外部环境成功视为单元测试替代品。

## 5. 校验结论

- 与当前实现一致：根 Run、typed state、runner、错误模型、键收敛和 22 项回归均已存在。
- 已解决计划冲突：统一图属于 P0-B；持久化 interrupt/resume 与外部副作用幂等属于 P1。
- 下一项：E1，先建设固定 Fixture；完成后再进入 E2。
