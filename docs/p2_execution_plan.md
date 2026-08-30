# P2：受控工具、可追溯研究与 Skills 执行计划

> 状态：进行中；E1–E5 已完成，受注册 Search Tool 与框架 `read_skill` 兼容验证留待后续独立工作。
> 日期：2026-08-29
> 依据：[plan.md](plan.md)、[development_log.md](development_log.md)、[p1_execution_plan.md](p1_execution_plan.md) 与当前代码。

## 1. 目标、范围与不可变边界

P2 在 P1 已交付的可恢复 Workflow 底座上，交付可追溯的真实研究能力和受控 Tool/Skill 边界。它不把搜索、网页读取或模型规划直接塞进 `SupervisorWorkflowService`，也不让 Agent 自行修改 Run、checkpoint、预算或持久化状态。

本阶段的架构分工如下：

| 层 | P2 职责 | 禁止事项 |
|---|---|---|
| Workflow / 服务层 | 状态推进、child run、Tool 调用授权、预算/超时/并发、审计、来源持久化 | 根据自由文本直接执行 Tool；由 Agent 决定幂等或 checkpoint 提交 |
| Tool Policy Gateway | 协议/域名/IP 校验、重定向复检、调用配额、超时、重试、审计 | 被 `returnDirect`、Agent 或业务 Mapper 绕过 |
| Tool Adapter | 搜索、网页读取与内容规范化，输出受限 DTO | 公开 HTTP 原始响应、执行网页中的指令、直接写数据库 |
| Research Agent | 在已授权 Tool 的结果上提出查询、去重、归纳事实/冲突并形成 `ResearchBundle` | 伪造来源、访问未授权 Tool、直接推进 Run |
| Skill Registry | 版本化 Skill 契约、输入输出 Schema、允许 Tool、预算与验收条件 | 加载第三方可执行代码或替代框架生命周期管理 |

P2 不交付章节并行写作、Fact Checker/Style Reviewer 双评审、局部返工、文章版本 diff、前端 DAG 或生产级搜索供应商的全量接入；这些保留给 P3/P4。现有图片 Tool 的业务行为和 P1 图片副作用幂等路径必须保持兼容。

## 2. 现有前置条件与影响范围

- `WorkflowRunner`、typed `WorkflowState`、`AgentRun`、checkpoint 与节点执行幂等已存在；P2 只能通过这些应用边界接入，不暴露 Spring AI Graph 类型。
- `SupervisorWorkflowService` 保留旧 `Runnable` 兼容入口，同时提供结构化 `ResearchUseCase` 入口；后者只能接收受控的 `ResearchRequest`，而不是在该类中新增 HTTP 客户端。
- `SupervisorPlan` / `SubtaskSpec` 已有 `allowedTools` 与 `requiredTools`；所有 P2 Tool 调用必须先通过这两层授权的交集校验。
- MySQL 初始化目前到 `07_add_agent_workflow_persistence.sql`；任何新表采用新的、可重复执行的增量脚本，并同步 Compose 与 README 的已有数据库升级说明。
- 默认 `mvn test` 必须保持离线；真实 Search/Web Reader 仅可由显式 profile 或受控集成测试触发，测试日志不得输出密钥、完整网页正文或敏感 Token。

## 3. 分步实施、验收与回滚

每一个 E 在开始代码实施前必须另建 `p2-e{N}_execution_plan.md`，写明该 E 的精确修改范围、接口/数据模型、测试矩阵、验证与回滚；完成评审和与本总计划的边界校验后才可实施。本文不替代各 E 的执行文档。

### E1：研究与 Tool 的框架无关契约

- 目标：先定义 `ToolId`、`ToolRequest`、`ToolResult`、`ResearchSource`、`ResearchBundle`、`ToolCallAudit` 与失败分类等 DTO/接口，不接入公网。
- 修改范围：`agent/tool`、`agent/research`、`agent/policy` 等项目自有包及离线单元测试；保持现有 `agent/tools/ImageGenerationTool` 兼容，不强制迁移图片服务。
- 关键约束：来源至少保存 canonical URL、标题、发布者（可空）、抓取时间、内容哈希、摘要、检索查询和可信/冲突/不可用状态；正文向 Writer 传递来源 ID 与有限摘要，不传递无限网页内容。
- 验收：结构化契约拒绝空 Tool ID、超预算、未授权 Tool、缺失来源 URL 和超长摘要；任一 `ResearchBundle` 都能列出所用来源和未验证结论。
- 回滚：新 DTO/接口不接入主图；删除引用即可回到 P1 行为，无数据迁移。
- 实际结果（已完成）：新增 `ToolId`、`ToolCallRequest`、`ToolCallResult` 与 `ToolAuthorization`，以及 `ResearchSource`、`ResearchSourceStatus`、`ResearchBundle`。其中 Tool 请求在构造期拒绝空 ID/输入和非正调用预算，授权入口拒绝计划未允许的 Tool；来源强制 canonical URL、抓取时间、内容哈希、查询、状态和最大 4,000 字符摘要，研究包要求至少有来源或明确的未验证项。`ResearchToolContractTest` 覆盖合法交接、缺 URL、超长摘要、越权 Tool 和超预算；尚未接入 HTTP、数据库、Supervisor 或主图。

### E2：Policy Gateway 与安全 Web Reader

- 目标：在任何 HTTP Tool 之前建立唯一 Gateway，默认拒绝未知 Tool、未知协议、私网/回环/链路本地/云元数据地址和超出限制的响应。
- 修改范围：Tool Registry、Policy Gateway、URL 验证器、HTTP Reader Adapter、可计数 Fake Tool 与安全回归测试；不先绑定某一搜索供应商。
- 安全策略：仅允许 `https`（如需 `http`，仅测试环境显式开启）；每次重定向重新解析并检查 DNS 结果；拒绝 IPv4/IPv6 loopback、private、link-local、multicast、unspecified 与云元数据地址；限制重定向次数、连接/读取超时、响应字节数和文本长度；剥离脚本、样式和 HTML 指令，仅输出规范化文本与元数据。
- 可靠性：读取类 Tool 采用有限重试且只对可安全重试的错误生效；并发、超时和剩余预算在 Gateway 统一扣减并审计。`returnDirect` 不允许用于 Research/Web Reader。
- 验收：离线 Fake HTTP 覆盖越权 Tool、超预算、重定向到私网、DNS 变化、恶意网页提示、超大响应、超时和重试上限；所有拒绝均产生脱敏审计结果而不泄露 Token。
- 回滚：Registry 默认不开启任何真实 Tool；禁用 Gateway 绑定即可恢复 P1 的研究占位路径，不影响图片 Tool。
- 实际结果（已完成）：已按独立 [p2-e2_execution_plan.md](p2-e2_execution_plan.md) 实施默认拒绝的 Registry/Gateway、URL/DNS 安全校验、手动重定向复检、受限 JDK transport、HTML 规范化与脱敏审计；E4 已将其作为 Spring Bean 装配，但没有绕过 Gateway 的直连入口。`ToolPolicyGatewayContractTest` 以 Fake DNS/HTTP 覆盖核心安全矩阵，默认离线回归通过。

### E3：来源持久化与可回放测试基座

- 目标：持久化研究来源和 Tool 审计，使研究 child run 可追溯、可重放且不依赖将完整网页塞入 checkpoint。
- 修改范围：新增 `research_source`、`tool_call_audit`（如确有必要再增关联表）迁移、实体/Mapper/Service、Docker 初始化顺序、Testcontainers 测试和 README 升级说明。
- 数据约束：来源以 `(runId, canonicalUrl, contentHash)` 或等价业务唯一键去重；审计保存 Tool、请求摘要哈希、结果状态、耗时、预算消耗、错误分类和关联 node execution，不保存密钥或完整敏感正文。
- 测试数据：建立至少 10 个固定的本地网页/搜索回放样本，分别覆盖正常来源、重复来源、来源冲突、无效引用、恶意注入、重定向阻断和超长内容；这些样本与最终质量评测集分离。
- 验收：Testcontainers 证明重复读取不会产生重复有效来源记录；来源可按 run 查询；失败审计不妨碍后续受控重试；默认测试仍不启动 Docker。
- 回滚：新表仅由 P2 路径使用；禁用研究入口即可停止写入，不删除已有来源和审计数据。

### E4：Research Agent 与 Supervisor 真实研究接入

- 目标：以 `ResearchUseCase`/端口替换当前 research `Runnable` 占位，让 Supervisor 的已验证“研究/跳过研究”路由执行真实、受控的研究任务。
- 修改范围：Research Agent、查询计划/结果 Schema、研究用例、Supervisor 适配、typed state 的最小扩展、Fake Model/Tool 及路由回归测试；不在本阶段实现章节 Writer 的来源消费。
- 执行流程：Supervisor 输出受校验的研究需求 → Workflow 创建/复用 research child run → Research Agent 产生受限查询计划 → Gateway 调用 Tool → 服务层持久化来源/审计 → Agent 归纳 `ResearchBundle`（事实、来源 ID、冲突与未验证项）→ Workflow 写入状态并继续既有写作路由。
- 验收：需研究请求只经 Gateway 调用已授权 Tool 并产生来源；免研究请求不创建 research child run、无 Tool 审计；Tool 失败时输出明确降级/失败状态而非伪造引用；恢复重试复用已有成功的来源与节点结果。
- 回滚：以 feature binding/路由开关回退到 P1 研究占位；保留已存来源和审计供排障。
- 实际结果（已完成）：新增 `ResearchRequest` 与 `RegisteredSearchResult`，使 Web Reader URL 只能来自已注册的 Search 结果，禁止从 `SubtaskSpec.instruction` 解析 URL。`GatewayResearchUseCase` 通过 Gateway 保存成功来源、在失败时只输出未验证项、在重试时复用已保存来源；Spring 完成 Registry/Gateway/审计/UseCase 绑定。离线与 Testcontainers 回归均已通过。真实 Search 供应商和 Writer 对来源的消费仍不属于 E4。

### E5：版本化 Skill Registry 与 P2 收口

- 目标：交付最小、仓库内置的声明式 Skill Registry，为 P3 的 Writer/Reviewer 提供可复用 SOP，而非建设插件运行时。
- 首批 Skill：`research-brief`、`longform-article`、`fact-check`、`visual-plan`、`citation-format`；P2 只有 `research-brief` 需要真实执行，其余先交付声明、Schema 与离线验证。
- 修改范围：Skill 定义、版本、输入输出 Schema、allowedTools、预算、验收条件、解析器与契约测试；若框架 `ReactAgent.read_skill` API 的兼容性未被当前版本验证，则只保留框架无关项目层 Registry，另立小范围兼容验证，不阻塞 P2 主交付。
- 验收：Skill 版本不可变；未知版本、越权 Tool、无预算或 Schema 不合格的 Skill 被拒绝；同一 Research Skill 可被不同 Run 复用；README、`plan.md`、开发记录和 SQL/Compose 与实现一致。
- 回滚：Registry 不支持第三方动态代码；停用 P2 绑定后，既有文章工作流与 P1 持久化数据保持可用。
- 实际结果（已完成）：已按独立 [p2-e5_execution_plan.md](p2-e5_execution_plan.md) 新增项目层声明式 Registry、输入/输出字段 Schema、精确版本解析、Tool 白名单与调用预算校验，并交付五个内置 `1.0.0` Skill。Registry 不执行动态代码，不绕过 Gateway，也不绑定未验证的 `ReactAgent.read_skill` API；后续 P3/P4 可消费该契约。

## 4. 阶段门禁

1. 每个 E 完成前运行 `git diff --check` 与默认 `mvn test`；记录测试数量、失败和错误。
2. E2 必须覆盖 SSRF、重定向复检、提示注入、超时、响应大小、权限和预算的离线安全回归。
3. E3 必须以 Testcontainers MySQL 验证来源/审计持久化与去重；Docker 不可用时不得将该项标为完成。
4. E4 的真实 Tool 集成测试显式触发，不使用真实密钥作为默认回归前置条件；任何真实来源均记录 URL/时间/哈希，不记录密钥或原始敏感响应。
5. 任一 Agent 输出在执行前都经代码校验；Agent 不得决定状态版本、checkpoint 消费、幂等键、预算硬限制或网络安全策略。

## 5. 完成定义与交接

P2 完成时，研究请求可在现有 Workflow 中创建可恢复的 child run，经唯一 Policy Gateway 使用受授权 Tool，持久化可追溯来源与脱敏审计，并产出带事实、来源 ID、冲突和未验证项的 `ResearchBundle`。项目同时具有可验证、版本化的内置 Skill Registry。P3 只能消费这些结构化来源和 Skill 契约；不得绕过 P2 重新以自由文本或直接 HTTP 调用实现研究。
