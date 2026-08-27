# P0-B E3：统一图迁移可执行文档

> 状态：已完成
> 日期：2026-08-27
> 上游：[P0-B 执行计划](p0-b_execution_plan.md)

## 1. 目标与完成定义

将现有标题、标题确认后大纲、确认大纲后正文配图三个预编译 StateGraph，收敛到 `agent/graph` 中唯一的文章工作流适配器。对 `WorkflowRunner` 保持三个阶段方法，因而 HTTP API、SSE 消息和人工确认交互不变。

完成后，同一个逻辑图定义必须支持以下受控状态迁移：

```text
PENDING → 标题节点 → WAITING_FOR_APPROVAL
WAITING_FOR_APPROVAL + selectedTitle → 大纲节点 → WAITING_FOR_APPROVAL
WAITING_FOR_APPROVAL + outline → 正文/配图/合成节点 → COMPLETED
```

图不得根据已有草稿自动跨越审批节点。调用阶段与所需输入不匹配时，仍返回 `INVALID_STATE`；节点/框架错误仍转换为 `GRAPH_EXECUTION`。

## 2. 范围、设计与不变量

- 新增 `agent/graph/ArticleWorkflowGraphAdapter`，由它独占 StateGraph、CompiledGraph、遗留 `ArticleState` 映射和节点装配。`ArticleWorkflowRunner` 仅处理 typed state、Run 生命周期、结果、错误和指标。
- 适配器使用一份固定的文章节点拓扑定义（标题、大纲、正文、图片分析、并行配图、合成），并以显式阶段入口运行相应连续片段；阶段入口不是业务 API 的新增字段，也不向 Controller/SSE 泄漏框架类型。
- 统一图不使用 checkpoint 处理审批。每个 `WorkflowRunner` 阶段调用都将 typed state 与内部阶段入口传入同一个图的路由节点；条件边只执行该阶段允许的连续分支。人工审批仍由下一次 API 调用带入的 selected title / outline 与 Runner 的状态校验控制。
- 仅迁移图适配层与 `ArticleWorkflowRunner`。不改 Controller、`ArticleAsyncService` 的调用协议、SSE 枚举、数据库 schema、Compose、模型请求或图片 Tool 行为。
- 框架基线固定为 Java 21、Spring AI Alibaba `1.1.2.2`、Spring AI `1.1.2`。

## 3. 实施步骤

1. 先新增无网络契约测试：验证统一适配器按阶段只运行允许的节点，标题和大纲审批不会被绕过，并保持现有标题/大纲/正文配图结果与 SSE 消息。
2. 创建图适配器：`route` 节点使用条件边路由至标题、大纲或正文配图分支；生产 Bean 初始化时预编译，非 Spring 测试按需惰性初始化。
3. 将 `ArticleWorkflowRunner` 改为依赖适配器，而非直接依赖编排器；保留其现有错误转换和 E2 metrics 测量边界。
4. 保留一个仅限包内/过渡用途的旧图路径开关或委托，使出现兼容问题时可恢复三图实现；默认路径必须为统一适配器。
5. 运行 `git diff --check`、`mvn test`，并将实际测试数、失败数、错误数及已知 Windows 编译器资源关闭现象记录至开发日志。

## 4. 验收与回滚

验收：

- 固定 typed state 完整经历三次调用后得到标题、大纲、正文、图片交付物和 `COMPLETED` 状态。
- 标题未选择时的大纲调用、未确认大纲时的正文调用均被拒绝，且不触发越界节点。
- 既有 SSE 回归、E2 模型调用计数和所有默认无网络测试通过（不少于当前 24 项）。
- StateGraph 类型不再出现在 `workflow` 或业务 `service` 包。

回滚：将 `ArticleWorkflowRunner` 的适配器依赖切回保留的三图过渡实现；不需要数据迁移，也不影响已保存的 Article/AgentRun 状态。若统一图实现依赖 checkpoint 才能满足人工审批，则停止本项并将该能力移交 P1，而不是扩大 P0-B 范围。

## 5. 明确不交付

- 持久化 checkpoint、进程重启恢复、Tool 幂等、取消传播与恢复并发控制（P1）；checkpoint 不再作为 P0-B 审批实现的一部分。
- 外部 Tool 超时、重试、预算、审计和来源研究（P2）。
- Token/成本指标、前端展示与线上监控（后续阶段）。

## 6. 实施结果

- `ArticleWorkflowGraphAdapter` 以单一预编译 StateGraph 的 `route` 条件边选择标题、大纲、正文配图三个阶段分支；`ArticleWorkflowRunner` 仅依赖内部执行边界，审批仍由 typed state 前置校验负责。
- 并行图片契约测试改用与生产一致的 `ReplaceStrategy`；生成器也改为每个来源任务返回局部结果、主线程统一归并，避免依赖异步任务共享结果集合。
- `mvn test` 通过：24 tests、0 failures、0 errors。 
