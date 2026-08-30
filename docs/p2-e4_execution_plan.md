# P2 E4：Research Agent 与 Supervisor 真实研究接入执行计划

> 状态：已完成
> 日期：2026-08-29
> 依据：[p2_execution_plan.md](p2_execution_plan.md)、[p2-e2_execution_plan.md](p2-e2_execution_plan.md)、[p2-e3_execution_plan.md](p2-e3_execution_plan.md)。

## 1. 目标与范围

以 `ResearchUseCase` 替换 `SupervisorWorkflowService` 当前的 `Runnable research` 占位：研究请求创建/复用 research child run，经 E2 `ToolPolicyGateway` 调用已授权 Tool，使用 E3 服务持久化来源和审计，最终产出结构化 `ResearchBundle`。免研究路由不得创建 child run 或触发 Tool。

本 E 不接入真实 Search 供应商、不让模型生成任意 URL、不修改既有标题/大纲/正文 API，也不实现 P3 Writer 对引用的消费。

## 2. 设计与约束

- 新增 `ResearchUseCase` 项目接口及 Fake 实现，输入为 parent run、受校验 `SupervisorPlan` 和明确研究查询；输出 `ResearchBundle`。
- 查询只来自 Workflow/结构化计划的有限字段，URL 只可来自已注册 Search 结果；Web Reader 必须通过 Gateway。
- Research child run、节点幂等与 checkpoint 继续复用 P1 服务；来源经 `ResearchSourceService` 保存，审计经 Gateway sink 保存。
- Tool 失败生成显式未验证项或可分类失败，不生成伪引用；恢复重试复用已有来源/节点结果。

## 3. 实施与验收

1. 定义 UseCase、查询/结果 DTO、Research Bundle mapper 和 Fake Tool/Model。
2. 将 Supervisor research 参数替换为受注入 UseCase，保持现有路由和 Writer 调度兼容。
3. 增加离线路由测试：研究路径产生 child run、来源与 Bundle；免研究路径零 Tool；越权/失败路径无伪引用。
4. 增加持久化集成测试：同一节点重试复用已保存来源，审计可按 run 查询。
5. 执行默认与 Testcontainers 回归，更新 README/计划/开发记录。

## 4. 实施结果

- `ResearchRequest` 与 `RegisteredSearchResult` 将查询、候选 URL、Tool 白名单和调用预算固定为结构化输入；`GatewayResearchUseCase` 不再从 `SubtaskSpec.instruction` 或任何自由文本提取 URL。
- Web Reader 只接收已注册 Search 结果的 `canonicalUrl`，并继续经唯一 `ToolPolicyGateway` 执行。成功来源以查询、内容哈希和持久化来源 ID 返回；Tool 失败只生成未验证项，不保存或返回伪引用。
- `SupervisorWorkflowService` 的结构化入口在研究路由中要求 `ResearchRequest`，并继续将稳定的 research child run ID 交给 UseCase；免研究路由不会调用 UseCase。
- Spring 已装配严格 Policy、URL 安全校验、Web Reader、封闭 Registry、持久化审计 sink 和 `ResearchUseCase`。未接入真实 Search 供应商；Search 结果仍须由后续受注册的 Search 边界提供。
- 来源持久化重试会先按 research run 和候选 canonical URL 复用已保存来源，避免重复 Web Reader 与审计写入。

## 5. 验证结果

- 离线 `GatewayResearchUseCaseTest` 覆盖受控候选 URL 成功来源持久化、未授权零 Tool、失败无伪引用和持久化来源复用；`SupervisorWorkflowServiceTest` 覆盖 research child run 与免研究路由。
- 默认 `mvn test`：48 项、0 失败、0 错误。
- `mvn test -Ppersistence-integration -Dtest=AgentCheckpointPersistenceIntegrationTest`：Testcontainers MySQL 8 项、0 失败、0 错误，包含重试只读取一次、只保存一个来源且只写一条审计的断言。

回滚：关闭新的 UseCase 绑定并恢复 P1 占位路径；来源、审计与已有 Run 保留，不删除数据。
