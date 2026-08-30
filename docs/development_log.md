# 开发记录

## 2026-08-31 P4 收口 C3：真实 MySQL 事件验证与序号修复

- 宿主 Docker Desktop 已验证可用；`AgentCheckpointPersistenceIntegrationTest` 在 Testcontainers MySQL 中运行 14 项、0 失败、0 错误、0 跳过。
- 新增的 Event 测试首次发现 `LAST_INSERT_ID()` 在连接池首次 INSERT 后可能保留旧连接值，导致 run 的第一条事件序号不是 1；已改为读取 `agent_event_sequence.nextSequence`，并同步修正 Context Snapshot 计数器。
- MVC 回放测试覆盖 `afterSequence`、文章授权和安全 payload；MySQL 测试覆盖持久化序号、增量读取和敏感字段不落库。

## 2026-08-30 P4 收口 C2：可信模型调用测量

- 新增 `agent_model_call_metric` append-only 迁移与项目 DTO/服务，Token/usage 缺失保留 NULL；模型名和 usage 仅从 Spring AI 实际 response metadata 读取，流式调用记录第一条非空 chunk 的时延。
- 现有 `WorkflowMetricsCollector` 的 run/stage scope 在阶段结束后持久化模型调用测量。当前模型端未实现自动重试，明确持久化 `retryCount=0`，不根据调用数量伪造重试数；成本仍留待 P5 版本化价格表。

## 2026-08-30 P4 收口 C1：计划与实现对齐

- 修正 `plan.md` 中 P4 E1–E6 的完成状态：现有 Event、运行详情、Artifact Manifest、Context Snapshot 和治理面板均保留为“部分实现”，明确记录开关、缺失数据源与测试缺口。
- 不再将 `run://` 逻辑地址、`0→0` Token 快照、未采集的成本/首 Token 或未接入的 Flow Hooks 宣称为完整交付；C2/C3 将补可信测量和真实持久化/API 验证。

## 2026-08-30 P4 E6：专项测试与验收收口

- 新增 `ContextSnapshotSanitizerTest`、`AgentGovernanceMetricsCalculatorTest` 并保留 Event payload 脱敏测试，验证凭据/敏感信息清除、摘要限长、P50/P95、失败率和未采集指标语义。
- 三项 P4 专项测试通过；此前默认后端回归 75 项和前端生产构建也已通过。P4 E1–E6 均先建立执行文档后实施并完成验收记录。

## 2026-08-30 P4 E6：回归验收中的 ContextSnapshot 兼容修复

- 默认后端回归 75 项、0 失败、0 错误、0 跳过，前端生产构建通过；首次重编译的 Windows 编译器资源关闭问题仍按既有方式未改代码重跑通过。
- 回归发现 P4 E4 覆盖了 P1 的 `agent.context.ContextSnapshot` 契约，已恢复其原有构造协议并将 P4 观测 DTO 改名为 `ObservabilityContextSnapshot`，避免破坏已有 Run Artifact 契约。
- E6 仍在实施：需要为新增事件、摘要与治理聚合补充针对性自动化测试后才可完成 P4 验收。

## 2026-08-30 P4 E5：运行指标与管理治理

- 新增管理员 Agent 治理聚合：仅基于 `agent_log` 状态和耗时生成成功率、平均耗时、P50/P95、慢节点和失败率提示，最大读取 10,000 条记录。
- 未持久化的 Token、成本和首 Token 明确标识未采集，不把正文长度或推算值伪装成真实指标；管理端不显示 Prompt、输入/输出或错误正文。

## 2026-08-30 P4 E3/E4：Artifact 面板与安全 Context Snapshot

- Artifact 面板只展示 P3 已持久化的版本/Manifest；当前 `run://` 逻辑地址均标记为不可下载，未伪造对象存储链接。
- 新增默认关闭的 Context Snapshot 旁路：checkpoint 后记录脱敏阶段摘要和真实可得的 Token 统计（首批为未知 `0→0`），不保存 Prompt、完整 checkpoint 或网页正文。
- 新增文章详情中的交付物和上下文面板。后端编译、前端生产构建均通过。

## 2026-08-30 P4 E2：运行详情与实时轨迹 UI

- 新增授权后的 Agent Run 安全 read-model，只返回父子 Run、checkpoint 与节点的状态投影，明确不向 UI 传递 state/result snapshot 或失败原文。
- 文章详情页新增运行轨迹面板：加载历史事件并订阅 E1 SSE，按单调 sequence 去重，组件卸载关闭流。
- 后端编译与前端 `npm run build` 均已通过；后端首次编译仍遇已知 Windows 编译器资源关闭问题，未改代码重跑通过。

## 2026-08-30 P4 E1：统一 Agent Event 与可重连回放

- 新增项目自有 append-only `agent_event` 及每 run 的持久化序号分配表；事件 payload 经白名单/长度清洗，拒绝保存 Prompt、模型正文、网页正文、认证头、Cookie 和密钥。
- 新增授权后的事件回放和 SSE 接口：先发送安全 Run 快照，随后按 `Last-Event-ID` 补发递增事件；新多订阅 emitter 与原文章 SSE 隔离，保留旧行为。
- Run、checkpoint、节点边界的事件发布为默认关闭、best-effort 旁路，发布故障不会回滚业务状态。默认 `mvn -q test` 通过；首次两次命中已知 Windows 编译器资源关闭问题，未改代码重跑后通过。

## 2026-08-30 16:25 — P3 E6 主流程迁移收口

- Testcontainers 已在 Docker Desktop MySQL 上真实运行：`AgentCheckpointPersistenceIntegrationTest` 13 项、0 失败、0 错误、0 跳过，覆盖 checkpoint stateVersion/CAS 单领取、取消优先、节点副作用重用、失败重试和 append-only Artifact 版本。
- 默认 `mvn test` 74 项、0 失败、0 错误、0 跳过；新 continue 的服务与 MVC 测试已包含在默认回归。
- 集成启动首次暴露 `ApprovedOutlineWritingRequestFactory` 未注册为 Spring Bean，已注册为无状态 component 后通过真实 ApplicationContext 验证。
- 收口前发现 continue 路径未送达旧 SSE 消息，已调整为缓冲图片图消息，仅在 Artifact、旧 Article 回填与 Run 完成后统一发送并 `complete` emitter；失败不发送伪完成事件。
- P3 E6 的 feature flag 默认仍为关闭，回滚只需保持 `article.agent.quality-loop.enabled=false`；既有 Run/Artifact 审计记录不删除。

## 2026-08-30  P3 E6.4：质量通过到图片交付的决策缺口

- E6.3 的 `WAITING_FOR_APPROVAL` checkpoint 与旧 HTTP“单次 generateContent 即完成图片”的流程发生冲突：旧接口没有质量通过后的图片继续入口。若同一次调用直接运行图片，将绕过 checkpoint/审批/恢复边界。
- 已新增 `p3-e6-e4_delivery_boundary_decision.md`，记录推荐方案为新增向后兼容的 quality-continue 接口，用户确认后经现有 `WorkflowRecoveryService.resume` 领取 checkpoint 再执行图片后半段。决定前不修改图片调用路径。

## 2026-08-30  P3 E6.4：图片后半段边界确认

- 检查旧 `ArticleAgentOrchestrator` 后确认 phase3 将 `content_generator` 与图片分析/生成/合成固定在同一 StateGraph；Runner 无法安全从中间节点继续。E6.4 执行文档已更新为先新增只含 `image_analyzer → parallel_image_generator → content_merger` 的独立图和端口，再由 P3 成功路径调用，禁止以重跑 `ContentGeneratorAgent` 作为捷径。

## 2026-08-30  P3 E6.3：accepted-content checkpoint 契约与恢复适配

- 新增 P3 checkpoint 白名单快照、Codec、Publisher 与恢复 Adapter。Gate 接受后发布稳定 checkpoint 并转 `WAITING_FOR_APPROVAL`；恢复仅验证 run/node/version 后回填 Markdown，绝不重跑模型、Writer、Revision 或 Artifact。
- `P3ContentCheckpointCodecTest` 与 `P3ContentRecoveryAdapterTest` 各 2 项通过。首次增量编译继续受已知 Windows 编译器资源关闭影响，未改源码重试后通过。
- 图片交接涉及旧 phase3 中 ContentGenerator 与图片节点耦合，先创建独立 E6.4 执行文档，尚未修改运行路径。

## 2026-08-30  P3 E6.2：Runner 受控分支接入

- `ArticleWorkflowRunner.generateContent` 已接入默认关闭的 `article.agent.quality-loop.enabled` 分支：关闭时保持旧 `executeContent` 路径；开启时创建/复用持久化 root run、读取实际 stateVersion、通过 typed approved-outline request 调用 `P3ContentWorkflow`，Gate 接受后只回填 Markdown 并返回 `CONTENT_QUALITY_ACCEPTED`。
- 新增 `QUALITY_REJECTED` / `QUALITY_EXECUTION` 错误码；P3 失败或拒绝不调用旧正文图、不回填成功内容。图片、SSE 和 checkpoint 发布仍留 E6.3/E6.4，避免将质量通过错误宣称为交付完成。
- `ArticleWorkflowRunnerTest` 4 项、0 失败、0 错误，新增断言验证 flag 开启时不调用 legacy content graph、回填 P3 Markdown 并同步持久化 run 当前节点。

## 2026-08-30  P3 E6.2：正文质量通过中间阶段

- 为避免 P3 正文质量通过但图片尚未生成时被误标为完整文章，新增 `WorkflowStage.CONTENT_QUALITY_ACCEPTED`。旧正文图继续仅在图片合成后返回 `ARTICLE_COMPLETED`；E6.2 的 feature-flag 分支将使用新中间阶段。

## 2026-08-29  P3 E6：主流程迁移独立执行文档

- 将 P3 主流程迁移重写为独立 `p3-e6_stategraph_migration_plan.md`，明确为待评审，确认前不修改 `ArticleWorkflowRunner`。文档固定全局 flag `article.agent.quality-loop.enabled=false`、免研究 Fact 审计、不允许部分交付、稳定 Outline→Task 映射、P3 状态回填、图片位置、SSE/checkpoint/HTTP 兼容与回退行为。
- 文档附默认回归和 Testcontainers 矩阵；主流程迁移只可按 Runner 分支、checkpoint/SSE、图片交接三步执行，不能把已经存在的 P3 UseCase 当成旧 HTTP 主链路已迁移。

## 2026-08-29  P3 E6.2：共享模型的受控写作/评审/修订适配器

- 基于用户确认的“首版共用当前 LiteLLM / `AiModelPort`”实现 `ModelSectionWriter`、`ModelFactChecker`、`ModelStyleReviewer`、`ModelRevisionAgent` Spring Bean。每个 Adapter 要求 JSON 输出后再构造 P3 DTO；Writer/Revision 复用来源引用校验，Reviewer 拒绝跨章节问题，Style Reviewer 不返回正文。
- 未研究时 Fact Adapter 不调用模型，返回 `FACT_ENHANCEMENT_NOT_REQUESTED` 的 MINOR 审计问题和 100 分，不阻断写作、不伪造引用；有研究时只把草稿引用与已注册来源交给模型。
- `ModelP3AdaptersTest` 2 项、0 失败、0 错误，覆盖 Writer/Revision 伪引用拒绝、免研究 Fact 审计和跨章节 Style 问题拒绝。默认 feature flag 仍关闭，尚未使旧 HTTP 主流程进入新分支。

## 2026-08-29  P3 E6.1：已审批大纲到质量写作的 typed 交接

- 按确认语义新增 `ApprovedOutlineWritingRequest` / Factory：只接受已确认标题和大纲，稳定生成 `SectionTask`，不从 Prompt 或大纲文本提取 URL。未提供研究包时构造明确的“未请求事实增强” `ResearchBundle`，Fact Reviewer 后续可据此输出审计信息但不应伪造来源或强制失败。
- 新增 `article.agent.quality-loop.enabled=false` 全局 feature flag，默认保持旧正文图。未开启或接入 Runner，因为当前生产代码尚无可复用的 P3 `SectionWriter`、Fact Checker、Style Reviewer、Revision Agent 实现；先把适配输入和开关固定，避免创建运行时必然失败的分支。
- `ApprovedOutlineWritingRequestFactoryTest` 2 项、0 失败、0 错误，覆盖稳定章节 ID、免研究显式状态及未审批大纲拒绝。首次主/测试增量编译遇到已知 Windows 资源关闭问题，未改源码重试后通过。

## 2026-08-29  P3 E5：受限质量闭环接入

- 新增 `QualityRevisionWorkflowUseCase`，将既有章节 Writer durable snapshot、Fact/Style Reviewer、Quality Gate、局部 Revision、不可变版本与 Artifact 发布串为受节点幂等控制的闭环。每轮仅把 Gate 标记章节传给 Revision；`ACCEPT` 发布当前版本，`REJECT_MAX_ROUNDS` 终止而不继续修改。
- Revision 与 Artifact 分别使用 `section-revision-{sectionId}` / `article-artifact-v{version}` 节点键重试复用。集成测试暴露 Artifact 节点把纯文本写入 MySQL JSON 快照列的问题，已改为 JSON 对象快照。
- Testcontainers `AgentCheckpointPersistenceIntegrationTest` 扩至 11 项、0 失败、0 错误：确认首轮失败后只修订目标章节，重试不重复 Writer、Revision 或 Artifact，版本与 Manifest 均可读取。两轮拒绝与部分章节失败策略尚未补齐；后者涉及是否允许部分文章交付，需在实现前确认产品语义。

## 2026-08-29  P3 E5：失败恢复与两轮终止收口

- 产品决策：不允许部分文章交付。任一 Writer 章节失败时，质量闭环不进入评审、版本发布或 Artifact 交付；成功章节保留 `SUCCEEDED` child node snapshot，失败章节保留 `FAILED`，相同 run/state 重试只执行失败章节。
- 新增 Testcontainers 覆盖上述失败/恢复路径，并覆盖连续两轮 Gate 请求 Revision 后第三次失败返回 `REJECT_MAX_ROUNDS`：只保留前两版，拒绝状态不写第三版或其 Artifact。持久化集成测试增至 13 项、0 失败、0 错误。
- 本次不直接替换旧文章 `StateGraph` 主图：新闭环复用既有 checkpoint-compatible 节点幂等边界，主图迁移会影响现有标题/大纲/图片 HTTP 流程，应作为独立任务评估与验证，避免以 E5 测试扩展名义混入高风险迁移。

## 2026-08-29  P3 E5：版本链与 Artifact 持久化阶段收口

- 新增 `agent_article_version`、`agent_artifact` MySQL 迁移及 Compose 初始化挂载，并提供 `AgentArticleArtifactService`。服务以 `runId + version` 和 `runId + version + artifactId` 唯一键约束追加写入：只能在已持久化父版本之后发布，完全相同的重试返回已有版本，任何不同草稿或 Manifest 覆盖都会拒绝。
- Testcontainers MySQL 首次暴露 `JSON` 列写回时会规范化字符串，导致同内容重试被错误判断为版本覆盖。比较已改为 JSON 树的结构等价。`AgentCheckpointPersistenceIntegrationTest` 现为 10 项、0 失败、0 错误，覆盖版本 1、版本 2 父链、三类 Artifact 哈希和同 Manifest 重试复用。
- 尚未宣称 P3 完成：当前 Revision/Review/版本服务仍是受限 UseCase，尚未接入单一 checkpoint Workflow；因此部分章节失败、两轮 Gate 终止和版本 API 的图级集成测试仍保留为 E5 后续工作。

## 2026-08-29  P3 E4：局部 Revision、版本链与 Artifact 契约

- 新增 `SectionRevisionUseCase`、`SectionRevisionRequest` 与 `RevisionAgent`。只有 Quality Gate 返回 `REVISE` 时才允许进入 Revision，且每个 Agent 输入严格限制为目标章节、该章节 `SectionTask`/`ResearchBundle` 与同章节问题；未知章节、非 `REVISE` 决定、伪造引用和任务错配均会在代码侧拒绝。未被标记章节不会传给 Revision Agent，归并后继续复用原 Draft。
- 新增不可变 `ArticleVersion` / `ArticleVersionChain`，版本必须从 1 连续追加、显式关联父版本、记录修改原因和创建时间。该实现保持为领域层 append-only 结构；未在没有确认模型的前提下新增文章版本表，持久化适配器及读取 API 将在 P3 E5 与 Run/Artifact 集成时处理。
- 新增 `ArticleArtifactManifestFactory`，为文章 Markdown、来源包和质量报告生成稳定 `run://` 逻辑地址及 SHA-256；当前不虚构图片 Artifact，待图片交付物接入后再登记。`SectionRevisionContractTest` 3 项、0 失败、0 错误，覆盖局部改写、未标记章节保持、非法 Gate 拒绝、版本链和 Manifest 哈希。

## 2026-08-29  P3 E3：并行 Reviewer 与有限 Quality Gate

- 新增 Fact/Style Reviewer 端口、结构化 `ReviewReport`/`ReviewIssue`、并行 Review UseCase 与 Fact Review 输入校验。Fact Checker 只能获得 Writer 的引用 ID 与 P2 ResearchBundle，未知引用在调用前拒绝；Reviewer 不返回改写后的章节。
- 新增由代码拥有的 Quality Gate：默认双评分均不低于 80 且不存在 blocker 才接受；失败时可请求两次局部返工，第 2 次返工后仍失败则返回 `REJECT_MAX_ROUNDS`，不允许无限循环。
- `QualityGateContractTest` 3 项、0 失败、0 错误。E3 无数据库写入；E4 将实现 Revision Agent、版本链和 Artifact Manifest。

## 2026-08-29  P3 E2：受限章节 fan-out/fan-in 与持久化重试

- 新增 `ParallelSectionWritingUseCase`，以 `SupervisorScheduler` 提供最大并发和完成顺序无关的稳定归并；每个章节由父 run 派生稳定 writer child run，并经 `AgentNodeExecutionService` 的 `section-writer` 节点保存草稿快照。
- 同一章节 child run、节点和 state version 重试时直接复用首次序列化草稿，不会再次调用 Writer。E2 当前要求全部章节成功；部分成功与降级将在 E3 的 Quality Gate 中定义，避免先引入模糊语义。
- 离线 `ParallelSectionWritingUseCaseTest` 2 项与章节契约测试通过。Testcontainers MySQL 集成测试扩展为 9 项、0 失败、0 错误，实际断言章节重试只调用 Writer 一次、只产生一个 node execution 记录。一次测试断言最初按父 run 文本查询 child node 的 UUID runId 而返回 0，已改为使用结果中的 child run ID 精确查询；实现行为未受影响。

## 2026-08-29  P3 E1：章节写作与引用交接契约

- 新增 `p3_execution_plan.md`，将 P3 拆为章节契约、受限 fan-out/fan-in、双 Reviewer/Quality Gate、局部 Revision/Artifact 和集成收口五步；E1 明确只消费 P2 `ResearchBundle`，不改主图、数据库或直接调用 Tool。
- 新增 `SectionTask`、`SectionWriterRequest`、`SectionDraft`、`SectionWriter`、`SectionDraftValidator`、`SectionFanIn`。章节所需证据以来源 ID 声明，Writer 只能返回 Bundle 中的唯一来源 ID；遗漏必需引用、伪造引用、任务错配以及重复章节均会被拒绝，归并只按 `sectionIndex` 排序。
- 离线 `SectionWritingContractTest` 3 项、0 失败、0 错误；新增代码不需要数据库或网络。下一步为 E2：将该契约纳入受限并行 child run，而不是扩散现有 `CompletableFuture` 调度。

## 2026-08-29  P2 E5：版本化 Skill Registry 收口

- 新增 `p2-e5_execution_plan.md`，将 E5 限定为项目层、仓库内置的声明式 Skill 契约；不在未做框架兼容验证时绑定 `ReactAgent.read_skill`，也不允许第三方动态代码、Skill 直连 Tool 或修改 Run/checkpoint。
- 新增五个 `1.0.0` 内置 Skill（research brief、longform article、fact check、visual plan、citation format）及精确版本 Registry、输入/输出 Schema、Tool 白名单、调用预算和验收条件校验。重复 id/version、缺失或未知字段、越权 Tool 和超预算均会被拒绝。
- `AgentConfig` 装配 Registry/Validator；`SkillRegistryContractTest` 3 项通过。默认 `mvn test` 实际 51 项、0 失败、0 错误；Testcontainers MySQL 实际 8 项、0 失败、0 错误，确认新增 Spring Bean 不影响既有持久化链路。P3/P4 后续只能从这个项目层契约消费 Skill，不新增绕过 Gateway 的执行入口。

## 2026-08-29  P2 E4：Research Agent 与 Supervisor 受控研究接入收口

- 修正 E4 的边界缺陷：删除从 `SubtaskSpec.instruction` 提取 HTTPS URL 的实现，新增 `ResearchRequest`、`RegisteredSearchResult`，将查询、注册候选结果、Tool 白名单与预算固定在结构化输入中。Web Reader 只消费注册 Search 结果的 canonical URL，不接受 Supervisor 自由文本。
- `GatewayResearchUseCase` 现在只经 E2 `ToolPolicyGateway` 调用 Web Reader，成功时保存并返回带持久化 ID 的 `ResearchSource`；失败时仅产生未验证项，绝不写来源或生成伪引用。重试会先查询同一 research run 的已保存候选来源，命中后不重复读取网页、不重复写审计。
- `SupervisorWorkflowService` 的结构化入口要求 `ResearchRequest` 并保留稳定 child run ID；免研究路径不调用 UseCase。`AgentConfig` 已完成 Policy、URL 校验、Web Reader、Registry、持久化审计 sink 与 UseCase 的 Spring 装配。E4 不接入真实 Search 供应商，后续 Search 边界必须产生注册候选结果。
- 验证：默认 `mvn test` 实际 48 项、0 失败、0 错误；`mvn test -Ppersistence-integration -Dtest=AgentCheckpointPersistenceIntegrationTest` 经 Testcontainers MySQL 实际 8 项、0 失败、0 错误，新增断言覆盖来源重试复用、Web Reader 仅一次与审计仅一条。首次增量编译仍受已知 Windows“无法关闭编译器资源”影响，未改源码重跑后通过。

## 2026-08-29  P2 启动：受控工具与可追溯研究执行方案

- 基于 P1 已完成的 Supervisor 路由、checkpoint、恢复互斥与节点幂等，新增 `p2_execution_plan.md`，将 P2 拆为研究/Tool 契约、Policy Gateway 与安全 Web Reader、来源/审计持久化、Research Agent 接入和版本化 Skill Registry 五个可独立验收的步骤。
- 方案明确真实研究只能替换 `SupervisorWorkflowService` 中的 research 占位边界，不能将 HTTP 客户端或自由 Agent 决策直接写入 Supervisor；Tool 调用必须经过唯一 Gateway，持久化来源和审计，默认测试保持离线。P3 将只消费 P2 的结构化来源与 Skill 契约。
- 本次仅完成 P2 的执行方案和文档索引更新，尚未声明任何真实 Search/Web Reader、来源表、Policy Gateway 或 Research Agent 已实现。

## 2026-08-29  P2 E1：研究与 Tool 框架无关契约

- 新增 `agent/tool` 的 `ToolId`、`ToolCallRequest`、`ToolCallResult`、`ToolAuthorization` 及 `agent/research` 的 `ResearchSource`、`ResearchSourceStatus`、`ResearchBundle`，为后续 Search/Web Reader、Policy Gateway 和 Research Agent 固定项目自有 DTO 边界，不暴露供应商 HTTP 或框架类型。
- Tool 请求强制 run、Tool、输入、允许 Tool 集和正调用预算；授权入口拒绝越权 Tool。研究来源强制 canonical URL、标题、抓取时间、内容哈希、检索查询、状态和受限摘要；研究包要求至少有来源或未验证项，避免下游将空结果或伪引用当成事实。
- 新增离线 `ResearchToolContractTest`，覆盖合法交接、缺失 URL、超长摘要、越权 Tool 和超预算。默认 `mvn test` 的首次主代码与第二次测试代码增量编译均遇到已知 Windows“无法关闭编译器资源”问题；未改源码的第三次执行完成，Surefire 当前 `com.passage.agent` 命名空间 45 项测试均为 0 失败、0 错误。E1 不调用公网、不写数据库，也不接入 Supervisor 主图；下一步为 E2 的唯一 Policy Gateway 与安全 Web Reader。

## 2026-08-29  P2 E2：独立执行文档

- 明确 P2 总执行计划只负责阶段路线与跨 E 边界；自 E2 起，每个 E 必须先建立独立 `p2-e{N}_execution_plan.md`，经范围、接口、测试矩阵、验证和回滚校验后才可开始代码实施。
- 新增 `p2-e2_execution_plan.md`，将 E2 限定为默认拒绝的 Policy Gateway 与安全 Web Reader：覆盖唯一入口、Registry、URL/DNS/重定向校验、超时/大小限制/有限重试、内容规范化和脱敏审计，并以本地 MockWebServer/Fake DNS 作为验证基座。文档明确不接入真实搜索供应商、数据库、Research Agent 或 Supervisor 主图。

## 2026-08-29  P2 E2：Policy Gateway 与安全 Web Reader

- 实施默认拒绝的 `ToolRegistry`、`ToolPolicyGateway`、`ToolPolicy`、`ToolCallAuditEvent` 和稳定错误分类。调用必须先通过 E1 的允许 Tool 集与 Registry 双重校验；审计仅保留 origin、路径哈希、结果、耗时、重试与大小，不保存查询参数、Token、请求头或响应正文。
- 新增 `UrlSafetyValidator`、可替换 `HostResolver`、`WebReaderToolAdapter` 和禁用自动重定向的 `JdkWebTransport`。Web Reader 仅允许 HTTPS/443、每次重试和重定向前重新解析与校验地址，拒绝 loopback、私网、链路本地、CGNAT、IPv6 ULA 等地址；只接受受限 `text/html`/`text/plain` 响应，移除脚本、样式、表单等内容并限制字节和文本大小。
- 新增离线 `ToolPolicyGatewayContractTest` 4 项，以 Fake DNS/HTTP 验证越权/未知 Tool、HTTP 拒绝、私网重定向二跳阻断、503 有限重试、HTML 规范化、超大响应和非文本 MIME；不访问公网。默认 `mvn test` 首次主代码增量编译命中已知 Windows 资源关闭问题，未改源码重跑后当前命名空间 49 项、0 失败、0 错误。E2 不接入 Spring、数据库、真实 Search 或 Supervisor；下一步 E3 必须先编写独立执行文档，再实施来源与审计持久化。

## 2026-08-29  P2 E3：独立执行文档

- 新增 `p2-e3_execution_plan.md`，限定 E3 只持久化 `research_source` 与脱敏 `tool_call_audit`，以 `(runId, canonicalUrl, contentHash)` 复用来源，以调用尝试保留审计；不写网页正文、查询参数、Cookie、Authorization 或密钥。
- 方案复用 P1 的 MyBatis-Flex、增量 SQL、Compose 初始化和 Testcontainers 基线；真实 Research Agent、Search 和 Supervisor 仍明确排除在 E3 之外。

## 2026-08-29  P2 E3：来源与审计持久化收口

- E3 的 `research_source`、`tool_call_audit` 迁移、持久化服务、Compose/README 升级说明与 Testcontainers 用例已完成；来源按 run、canonical URL 和内容哈希复用，审计不存查询参数、密钥、请求头或网页正文。
- Docker Desktop 实际可用。以允许访问 Docker 命名管道的方式运行 `mvn test -Ppersistence-integration -Dtest=AgentCheckpointPersistenceIntegrationTest`，Testcontainers MySQL 实际执行 7 项、0 失败、0 错误、0 跳过，包含 E3 新增来源去重与审计脱敏断言。下一步已先创建 P2 E4 独立执行文档，尚未实施 E4 代码。

## 2026-08-28  架构定位：混合式 Agent Workflow

- 复核 P1 已完成的持久化 Run、Supervisor 受限路由、checkpoint 恢复、取消优先与图片副作用幂等后，明确项目不应演进为由自由 Agent 自行编排全部步骤的系统，而是采用“Workflow 控制平面 + Agent 认知执行单元”的混合架构。
- Workflow/普通服务继续负责生命周期、状态迁移、HITL、checkpoint、取消、并发、预算硬上限、幂等和外部副作用提交；Agent 仅在受校验计划、结构化契约和 Tool 白名单内完成开放式规划、研究、写作、评审与局部返工。确定性图片、存储、Markdown 合成和数据库操作保持为 Tool/Service。
- 同步更新 `plan.md` 和 README：P1 成果定位为不可替代的控制底座；P2 的真实研究与 Tool 治理、P3 的并行 Writer/Reviewer/Revision 均明确在 Workflow 的状态、策略、质量门禁和循环上限内执行。此次为架构表述与后续验收边界澄清，不宣称 P2/P3 能力已实现，也不修改运行时代码。

## 2026-08-28  P1.5：去模板化与项目身份整理

- 新增 `p1.5_execution_plan.md`，先固定命名空间、构件坐标、兼容边界、验证门禁与回滚方式后实施；项目 Java 根包迁移为 `com.passage.agent`，Maven 坐标迁移为 `com.passage:passage-agent`，应用名同步为 `passage-agent`。
- Spring Boot Mapper 扫描、springdoc 包扫描、MyBatis XML namespace、生产与测试源码均已迁移到新根包。数据库表名、HTTP API 路径和配置键未改变；既有测试账号密码继续计算为相同历史盐值，以免破坏已有散列兼容性。
- 已移除当前工作树中的原模板站点、来源文案与作者注释，替换 README 标题、目录树与前端页脚；保留 Git 历史、既有 commit 作者和第三方版权文本。SQL 初始化中的演示头像改为空值，不再引用模板站点。
- 验证：`git diff --check` 通过；默认 `mvn test` 在 Windows 编译器资源关闭问题的两次无源码失败后第三次通过；使用临时、被忽略的前端 `env.ts` 配置完成 `npm run build`。`docker compose config --quiet` 通过。Docker 引擎访问授权后，`mvn test -Ppersistence-integration` 使用 Testcontainers MySQL 实际执行 6 项、0 失败、0 跳过。

## 2026-08-28  P1：执行文档与持久化模型起步

- 新增并校验 `p1_execution_plan.md`：P1 按持久化模型、Supervisor 条件路由、checkpoint/HITL 恢复、节点幂等与收口五步推进；明确 P2 才统一外部 Tool 的超时、预算与审计，避免将 P1 的一致性控制扩张为 Tool Gateway。
- E1 已开始：`agent_run` 增加状态版本，新增 checkpoint 与节点执行记录迁移，为乐观推进、单恢复者竞争和后续副作用幂等关联预留数据库约束；同时新增框架无关的 checkpoint 状态机契约。checkpoint Mapper/Service 通过状态与版本条件更新领取恢复权，子 Run 可持久化创建；节点执行记录的完整幂等提交/复用仍待完成，不能宣称已支持重启恢复。
- 引入显式 `persistence-integration` Maven Profile（默认 `mvn test` 不启动 Docker）；该 Profile 使用 Testcontainers MySQL 执行 Agent Run / checkpoint 迁移，验证父子 Run、状态版本推进和两个恢复者对同一 checkpoint 的竞争。最终 2 项数据库集成测试均通过。过程中发现并修复 JVM 与 MySQL 的时间精度/时钟差异会使 `updatedAt < createdAt` 或转移时间早于持久化时间的问题：持久化映射规范化时间顺序，状态迁移不会使用早于已持久化时间的时间戳。
- E1 收口：新增 `AgentNodeExecutionService`，用 `runId + nodeId + stateVersion` 的唯一键领取节点执行权。已成功的记录复用提交结果，运行中记录拒绝并发重放，失败记录只允许一个重试者条件转回运行状态。Testcontainers MySQL 集成测试扩展至 4 项并全部通过，覆盖重复结果复用、并发副作用最多一次与失败后重试；默认 `mvn test` 仍不启动 Docker。E1 完成，下一步进入 E2 的 Supervisor 业务路由接入。
- E2 收口：Supervisor 计划补充 `maxSubtasks`、`allowedTools`、任务依赖与所需 Tool，并在路由前以 `SupervisorPlanValidator` 拒绝超预算、空/重复 ID、非法/循环依赖和 Tool 越权。`SupervisorWorkflowService` 接入持久化子 Run：研究请求先进入无外部 Tool 的研究占位节点，免研究请求不创建研究子 Run；Writer 依赖按波次满足后才受限并发执行，fan-in 按章节顺序稳定归并。新增离线测试覆盖两条路由、父子 Run 与依赖波次；真实研究 Tool/来源和模型规划仍在 P2。
- 完整默认回归首次暴露既有并行图片 SSE 事件汇总竞态：两个并行分支对同一 Consumer 推送时，非线程安全消费者可能丢失事件或观察到乱序序号。`StreamHandlerContext` 现在把共享序列号递增与事件投递在同一 Consumer 锁内完成；`mvn test` 最终 33 项通过、0 失败、0 错误。
- E3 收口：新增 `WorkflowRecoveryService`，使恢复遵循“CAS 领取 checkpoint → 执行下一节点 → 成功消费；失败释放”的边界，且不向调用者暴露图框架类型。Run 取消改为按当前状态条件更新，取消成功后 READY/CLAIMED checkpoint 一律转为 `CANCELLED`；领取期间检测到 Run 已变更会释放领取，避免僵尸 CLAIMED 记录。离线恢复测试 5 项通过；Docker/Testcontainers MySQL 集成测试 5 项通过（含取消后拒绝恢复）。E4 将处理 Tool 已完成但 checkpoint 尚未提交时的副作用一致性。
- 计划调整：在 P1 收口与 P2 开始之间加入 P1.5“去模板化与项目身份整理”。用户确认已获原作者许可，因此计划覆盖 `com.passage.agent` 包名、Spring/MyBatis/springdoc 扫描字符串、Maven 构件元数据、前端 `codefather.cn` 页脚、README、示例配置和源码作者注释的统一替换或删除；保留 Git 历史与第三方依赖版权文本，不改数据库、HTTP API 或配置键，也不与 E4 混合。
- E4 收口：`ParallelImageGenerator` 的每个图片副作用现经 `IdempotentImageGenerationGateway` 进入 `AgentNodeExecutionService`。有持久化 Run 时以 `runId + 图片节点 + stateVersion` 保存成功结果，checkpoint 未推进导致的同版本重试直接复用首次 URL，不会再次调用图片 Tool；无 Run 的旧路径保持直连兼容，Tool 返回失败不被标记成功。离线网关/图片兼容测试通过；Testcontainers MySQL 集成测试增至 6 项并通过，包含“副作用成功、checkpoint 失败后重试”场景。P1 下一步为 E5 文档与交接收口。
- E5 收口：README、Compose、SQL 与 P1 执行文档已交叉核对；Compose 按顺序加载 Agent Run 和工作流持久化迁移。默认 `mvn test` 通过 36 项，Testcontainers MySQL 集成测试通过 6 项，`git diff --check` 无空白错误。P1 完成；下一独立阶段为 P1.5 去模板化与项目身份整理，之后进入 P2 的真实研究与 Tool 治理。
- P1 文档校验时默认 `mvn test` 通过：25 项测试、0 失败、0 错误；E1 基础落地后最终复跑通过 26 项测试、0 失败、0 错误。两次首次增量编译分别触发已知 Windows 编译器资源关闭问题，未改源码后的第三次运行通过。Docker Compose 在当前环境因缺少 `PEXELS_API_KEY` 未能解析，数据库集成测试待具备可用 Docker 配置后补充。

## 2026-08-27  P0-B E3/E4：统一图与收口验证

- 统一图改为单一 `StateGraph` 的条件边路由：每次阶段调用从 `route` 进入标题、大纲或正文配图分支，审批边界继续由 typed state 与 `WorkflowRunner` 校验，不再误用 checkpoint。
- 并行图片生成改为来源任务返回局部结果、主线程归并；契约图同步使用生产的 `ReplaceStrategy`，避免默认列表状态合并造成测试中的结果丢失。
- 补齐统一图节点异常映射至可重试 `GRAPH_EXECUTION` 的契约。普通、流式、结构化、图片部分失败、SSE 与错误分类均由无网络测试覆盖。
- JDK 21 下 `mvn test` 通过：25 项测试、0 失败、0 错误。P0-B 完成；持久化 checkpoint、重启恢复与 Tool 幂等仍明确留在 P1。

## 2026-08-27  P0-B E2：本地可比较运行指标基线

- 新增进程内 `WorkflowMetricsCollector`、`WorkflowExecutionMetrics` 和 `WorkflowStageMetrics`；每个 `WorkflowRunner` 阶段会记录耗时、模型调用次数、阶段结果状态与可选 `WorkflowErrorCode`，不新增数据库表、前端展示或外部遥测。
- 新增 `MetricsCollectingAiModelPort` 作为项目模型端口的 Spring `@Primary` 装饰器，仅在工作流测量范围内计数，因而不改变模型请求、SSE 报文或既有 `SpringAiModelAdapter` 的行为。
- 固定 Fake 三阶段工作流的调用基线为标题 1 次、大纲 1 次、正文配图 2 次；未选标题直接执行大纲会以失败状态记录 `INVALID_STATE`，没有模型调用。指标只用于相同本地输入下的回归比较，不能表述为线上性能。
- JDK 21 下 `mvn test` 最终通过：24 项测试、0 失败、0 错误。首次主代码和测试代码增量编译均触发已知 Windows 编译器资源关闭问题；未改源码重跑后通过。

## 2026-08-27  文档目录收敛

- 将计划、阶段执行文档、开发记录、框架兼容性、参考架构及 VIP/Stripe 说明统一迁入 `docs/`，新增 `docs/README.md` 作为索引；根目录仅保留项目入口 `README.md` 与协作规范 `AGENTS.md`。
- 已同步更新 README、计划与 AGENTS 中的路径引用。后续阶段执行文档按 `docs/p{阶段}_execution_plan.md` 命名并在进入对应阶段前创建、校验。

## 2026-08-27  P0-B E1：固定测试数据基座

- 新增测试专用 `ArticleWorkflowFixture`，集中合成文章输入、typed state、确定性 Fake Model 输出与“一个图片任务失败”的配图需求；三阶段业务回归、Runner 和并行图片测试不再各自构造这些共同样本。
- Fixture 不含密钥、真实用户内容或公网数据。网页抓取回放和来源样本必须等 P2 Research 的真实来源模型出现后再补，不能为了填充测试目录伪造引用。
- `mvn test` 最终通过：22 项测试、0 失败、0 错误。过程中先修复一次测试代码误删 `List` 导入的编译错误；随后首次测试增量编译受已知 Windows 资源关闭问题影响，未改源码重跑后通过。

## 2026-08-27  执行计划校验机制

- 新增 `p0-b_execution_plan.md`，把当前 P0-B 剩余工作拆成固定 Fixture、可比较指标、统一图迁移和收口验证四个可独立验收的步骤；后续开发先以该阶段文档校验范围、兼容边界、测试与回滚，再开始改代码。进入 P1 前再单独生成 P1 执行文档，避免提前规划过远。
- 校验发现原计划的“统一单图”和“checkpoint 恢复”容易混淆：统一图与进程内审批边界保留在 P0-B，持久化 checkpoint、重启恢复、幂等与 Tool 副作用一致性明确留在 P1。该取舍避免在没有持久化保证时提前宣称可恢复。

## 2026-08-27  P0-B：工作流结果与错误契约

- `WorkflowRunner` 现在返回 `WorkflowExecutionResult`（typed state + 完成阶段），`ArticleAsyncService` 只读取其 `state`，因此 API 边界可演进而不暴露 StateGraph 类型。
- Run 持久化的 `currentNode` 改为保存上述工作流完成阶段；正文完成后不再把已完成 Run 错误标记为 `CONTENT_GENERATING`。
- 新增 `WorkflowError`、`WorkflowErrorCode` 与 `WorkflowExecutionException`：非法审批/状态转换标为不可重试的 `INVALID_STATE`；图执行失败统一标为可重试的 `GRAPH_EXECUTION` 并保留原始 cause 给日志。契约测试覆盖阶段结果和非法大纲执行的错误分类。
- JDK 21 下 `mvn test` 最终通过：22 项测试、0 失败、0 错误；本次首次主代码及测试代码编译均分别受已知 Windows 编译器资源关闭问题影响，连续重跑后恢复正常。

## 2026-08-27  P0-B：StateGraph 状态键收敛

- 新增 `ArticleWorkflowKeys`，将遗留 StateGraph 的输入/输出键集中在过渡边界；编排器、标题/大纲/正文/配图/合成节点均改为引用该单一词表，避免节点各自维护字符串导致静默不兼容。
- 对外保持各节点既有常量名，因而图拓扑、已有调用方和 SSE 协议不变。该类只服务于当前 `Map<String, Object>` 框架适配；统一 typed graph 落地后应删除它，而非将其扩散为新的业务状态模型。
- JDK 21 下 `mvn test` 最终通过：22 项测试、0 失败、0 错误。源码变更后的第一次 Maven 编译仍触发已知 Windows 编译器资源关闭问题，未修改源码重跑后通过。

## 2026-08-27  P0-B：AgentRun 数据库迁移接入部署

- Docker Compose 的 MySQL 初始化序列新增 `06_add_agent_run_tables.sql`；首次创建数据卷时会自动建立 `agent_run`，与运行时代码的根 Run 创建保持一致。
- README 的本地初始化步骤补充该增量脚本，并明确已有数据库必须手动执行。MySQL 官方初始化脚本仅在空数据卷执行，因此已有 Docker 数据卷不会自动补表；部署升级前必须先完成该迁移。

## 2026-08-27  P0-B：StateGraph 预编译与复用

- `ArticleAgentOrchestrator` 改为在 Spring Bean 初始化期编译标题、大纲、正文配图三个图并复用，避免每个请求重新构图和编译。
- 为无 Spring 的 Fake 契约测试保留线程安全懒加载：生产路径由 `@PostConstruct` 预热，测试路径仅在首次调用时编译。图节点仍保持现有三阶段结构，统一单图将在后续迁移中完成。

## 2026-08-27  P0-B：异步阶段接入 WorkflowRunner 与 Run 状态

- 编排器启用时，`ArticleAsyncService` 的标题、大纲、正文配图三阶段改为通过 `WorkflowRunner` 执行，并以 mapper 在现有 DTO 与 typed state 间转换；原 `ArticleAgentService` 降级路径未修改。
- 每个阶段结束后将 Run 同步为等待标题审批、等待大纲审批或完成；任一阶段异常同时标记文章与 Run 为失败。为满足 typed input 约束，阶段 2/3 从文章记录补回 `topic`。
- 当前持久化只同步状态与当前节点，未写入 checkpoint/state snapshot；该部分与恢复幂等控制仍在 P1 完成。

## 2026-08-27  P0-B：根 AgentRun 持久化

- 新增幂等 SQL 脚本 `sql/add_agent_run_tables.sql`，创建 `agent_run` 表保存 root/parent Run、状态、当前节点、checkpoint 标识、状态快照和失败信息；既有文章与日志表未改动。
- 新增 `AgentRunRecord`、Mapper 与 Service；创建文章任务时以 `taskId` 作为 root `runId`，在同一事务创建 `PENDING` 根 Run，确保不会出现文章已创建而运行记录缺失的状态。
- 子 Run、checkpoint 状态写入和恢复并发控制仍属于 P1；部署前须先执行新增 SQL 脚本，当前默认测试不连接数据库。

## 2026-08-27  P0-B：子任务、上下文与交付物契约

- 新增 `AgentSubtask`，强制声明父 Run、稳定顺序、允许 Tool、Token 预算和验收条件，避免以无边界 Agent 群聊表示章节任务。
- 新增不可变 `ContextSnapshot` 与 `ArtifactManifest`；前者仅保存摘要、已确认事实、决策和待办，后者登记文章、来源、图片和质量报告等交付物位置及可选哈希。
- 新增契约测试，验证子任务、快照和交付物可关联至同一 Run，并拒绝缺少正预算的子任务。当前均未持久化；下一步在不破坏已有 `article` / `agent_log` 的前提下增加 Run 数据模型。

## 2026-08-27  P0-B：WorkflowRunner 统一入口

- 新增项目 API `WorkflowRunner` 与过渡实现 `ArticleWorkflowRunner`：以 typed `WorkflowState` 为输入/输出，通过唯一 mapper 边界委托当前 `ArticleAgentOrchestrator`，调用方不再接触 `StateGraph`、`CompiledGraph` 或 `OverAllState`。
- Runner 将标题生成、大纲生成、正文生成之间的审批边界显式映射为 `AgentRunStatus.WAITING_FOR_APPROVAL`，并拒绝未选标题或未确认大纲时继续执行。
- 新增无 Spring / 网络依赖的契约测试，覆盖三阶段状态转移、交付物回写与非法阶段调用。`ArticleAsyncService` 暂未切换到该入口，等待 `AgentRun` 持久化，避免现有阶段 API 产生不可恢复的临时 Run。

## 2026-08-27  P0-B：Legacy ArticleState 适配边界

- 新增 `WorkflowStateMapper`，集中完成当前 `ArticleState` 与 typed `WorkflowState` 的双向映射，覆盖选题、风格、标题、大纲、正文、配图、封面与完整内容。
- 增加 round-trip 契约测试，确保新状态模型落地前不会改变现有服务/API 所消费的 DTO 字段。后续编排器迁移只允许通过此边界进出旧模型，避免散落字段复制。

## 2026-08-27  P0-B：Typed WorkflowState 与 Reducer

- 新增不可变、框架无关的 `WorkflowState`，按输入（选题/风格/图片权限）、草稿（标题/大纲/正文）和交付物（配图需求/图片/最终正文）分组；列表在构造时复制，避免图节点之间共享可变集合。
- 新增 `WorkflowStateReducer`，使标题、大纲、正文、配图和 Run 生命周期的更新显式化。当前仅作为新图边界，尚未替换现有 `ArticleState + Map<String,Object>` 运行链路。
- 新增无 Spring/网络依赖的 reducer 契约测试，验证状态转换会保留已经确认的输入与草稿。

## 2026-08-27  P0-B：AgentRun 生命周期契约

- 新增框架无关的 `AgentRun` 与 `AgentRunStatus`，定义根/子 Run 身份、审批等待、暂停、完成、失败、取消等状态转换，并禁止终态回退。
- 新增纯单元契约测试，验证子 Run 保留 root/parent 链路以及终态不可重新运行。当前未接入数据库、`ArticleAsyncService` 或 StateGraph；这些迁移将在 Typed WorkflowState 与运行入口设计完成后进行，避免半迁移造成双状态源。

## 2026-08-27  P0-U：图片失败边界、checkpoint 恢复与版本冻结

- 新增 `ParallelImageGeneratorContractTest`：一个图片任务异常时，其他成功任务继续完成、异步事件仍发布，最终按文章位置稳定归并。外部 Tool 的超时、重试、预算和审计不在该节点局部实现，统一留给 P2 Policy Gateway。
- 新增 `CheckpointCompatibilityTest`：`MemorySaver` 下图可在 `first` 节点后中断；以暂停快照的 `RunnableConfig` 调用 `resume()` 后从 `second` 节点继续，已完成节点不会重放。仅使用初始 `threadId` 恢复会丢失保存状态，该用法限制已记录到兼容性文档。
- P0-U 的依赖、模型、图、流事件、Supervisor Fixture、业务最小链路、图片失败边界及 checkpoint API 隔离门禁均已通过，因此冻结 `spring-ai-alibaba-agent-framework:1.1.2.2` 与 `spring-ai:1.1.2` 作为 P0-B 开发基线。
- JDK 21 下 `mvn test` 最终通过，共 14 项测试。新增测试源码后的首次 Maven 编译仍偶发 Windows“无法关闭编译器资源”；无源码变更重跑后稳定通过。

## 2026-08-27  P0-U：三阶段业务链路 Fake 回归

- 新增 `ArticleCreationWorkflowContractTest`，以确定性 `AiModelPort` 驱动现有 `ArticleAgentOrchestrator` 的标题、大纲、正文与空配图合成三阶段；验证标题结构化输出、状态交接及既有大纲/正文 SSE 消息兼容。
- 测试不启动 Spring Context，也不访问 LiteLLM、数据库或图片服务；图片生成通过空需求路径验证下游图节点与合成边界。
- JDK 21 下 `mvn test` 最终通过，共 12 项测试。新增测试源码后的首次编译仍出现已知的 Windows“无法关闭编译器资源”问题；无源码变更重跑后稳定通过。
- P0-U 剩余两项：图片 Tool 的失败/超时契约与 framework interrupt/checkpoint API 的隔离样例。

## 2026-08-27 计划与当前实现校正

- 对照当前实现与默认 Maven 测试结果（11 项通过），更新 `plan.md`：P0-A 标记为完成；P0-U 已完成的模型端口、流事件、图和 Supervisor Fixture 契约不再重复列为待办。
- 将“依赖版本冻结”与 P1/P2 的完整业务能力分离：冻结只要求既有业务最小链路、图片 Tool 失败边界与 checkpoint API 隔离样例；Skill Registry、Policy Gateway、完整恢复一致性仍在其对应阶段验收，避免升级验证阻塞后续开发。
- 调整下一步顺序为先收口 P0-U，再进入统一运行状态与 `AgentRun` 骨架；同时要求从 P0-B 开始采集运行指标，P4 仅负责展示。
- README 的模型配置来源统一为 LiteLLM，移除仍将通义千问写为必需项的过时说明。

## 2026-08-24  P0-A + P0-U：Java / LiteLLM 基线与框架升级验证

- 将模型客户端从 DashScope Starter 迁移为 Spring AI OpenAI Starter，经 LiteLLM 的 OpenAI 兼容接口访问模型；运行时变量统一为 `LITELLM_BASE_URL`、`LITELLM_API_KEY`、`LITELLM_MODEL`。
- 同步更新本地配置模板、生产配置、Docker Compose、启动脚本和 README。Compose 为 Linux Docker Engine 添加 `host.docker.internal:host-gateway` 映射，同时保持 Docker Desktop 默认可用。
- Maven 加入 Java 21 Enforcer 门禁，避免 JDK 17 在编译阶段才产生难以定位的错误。
- 候选依赖矩阵升级为 `spring-ai-alibaba-agent-framework:1.1.2.2` 与 `spring-ai-starter-model-openai:1.1.2`，与业务图改造保持分离。
- 新增无真实模型调用的 Spring Context 装配测试，以及默认排除的 `litellm-smoke` Maven Profile。后者只在三个 `LITELLM_*` 变量完整时验证 `/v1/models`、普通聊天和流式聊天接口。
- 已验证 IDEA 下载的 Microsoft OpenJDK `21.0.12.1` 可被 Maven 使用；Java 21 Enforcer 通过，候选依赖已解析，并通过不访问外部服务的 OpenAI 客户端装配测试。`docker compose config --quiet` 也已通过。
- Windows 环境在测试源码变更后的首次 Maven 编译偶发报告“无法关闭编译器资源”，但 class 文件已生成；无源码变更的离线复跑稳定通过。后续若再次出现，应先检查 IDE/杀毒软件是否占用 `target/test-classes`，再重跑；这不构成框架升级通过结论。
- 尚未执行 Docker 健康检查、真实 LiteLLM 普通/流式冒烟，以及 P0-U 的图、Tool、checkpoint 契约验证，因此暂不冻结版本矩阵。

## 2026-08-25  P0 真实环境验证跟进

- 使用 Microsoft OpenJDK `21.0.12.1` 临时设置 `JAVA_HOME` 后，`mvn --offline test` 通过：Java 21 Enforcer 与 LiteLLM OpenAI 兼容客户端装配测试均正常。
- 已以 `.env` 中的 LiteLLM 配置显式运行 `mvn -Plitellm-smoke test`。沙箱内连接被拒绝；获授权重试后连接仍在 10 秒内超时，说明当前代理地址不可达，尚未执行到模型列表、普通调用或流式调用。
- `docker compose ps` 显示 Docker daemon 未启动，故未执行 Docker 构建、健康检查及接口级验证。
- 后续前置条件：启动 LiteLLM 并确认 `LITELLM_BASE_URL` 从宿主机可访问；启动 Docker Desktop。之后重跑 LiteLLM 冒烟与 Docker 验证，再决定 P0-A 是否收口。

## 2026-08-25  P0 真实环境验证完成

- LiteLLM 容器恢复后，以宿主机地址 `http://localhost:4000` 运行 `mvn -Plitellm-smoke test` 成功。测试依次验证模型列表、一次普通聊天和一次 SSE 流式聊天；未记录密钥、提示词或生成正文。
- 使用 `maven:3.9-eclipse-temurin-21-alpine` 构建后端 Docker 镜像成功；镜像内 Maven 的 Java 21 Enforcer 已通过，编译目标为 `release 21`。
- 已执行 `docker compose up -d --build backend` 重建后端容器。后端与前端容器均已通过 Compose 健康检查；MySQL、Redis 继续保持 healthy。
- P0-A 的配置、构建、代理和容器健康基线已收口。尚未在已认证业务会话中补测“标题生成—流式正文”接口链路；P0-U 仍缺图、Tool、checkpoint 契约、依赖收敛记录和升级冻结结论，不能据此进入 P0-B。

## 2026-08-25  P0-U 候选依赖最小图验证

- 新增 `GraphCompatibilityTest`，在不访问模型、数据库或图片服务的条件下验证 `StateGraph 1.1.2.2` 的串行异步节点状态传递、并行分支汇合，以及 `stream` 节点事件输出。3 项图契约通过。
- JDK 21 下 `mvn --offline test` 通过，共 4 项默认测试；LiteLLM 冒烟仍由显式 profile 独立运行。
- 已执行依赖树检查：Alibaba Agent Framework 与 Graph Core 解析为 `1.1.2.2`；OpenAI Starter、Spring AI Model/OpenAI/Client Chat 及相关传递模块解析为 `1.1.2`，无 `1.1.0` 回落。详细矩阵见 `framework-compatibility.md`。
- 版本暂不冻结：项目尚无持久化 checkpoint / interrupt-resume、Supervisor / ReactAgent Skill Registry、Policy Gateway 异步 Tool 的实现边界，不能对这些未落地能力作兼容性结论。

### 回滚

若 `1.1.2.2 + 1.1.2` 在 Java 21 验证中出现核心兼容性问题，回退 `pom.xml` 中两个版本属性至 `1.1.0.0-RC2` 与 `1.1.0`，并保留 LiteLLM 配置迁移；不得将框架兼容补丁混入后续 P0-B 业务图改造。

## 2026-08-25  P0-U：模型调用端口与 Fake 契约

- 新增项目自有 `AiModelPort`，定义普通文本、流式文本和结构化结果三类模型调用；`SpringAiModelAdapter` 负责 Spring AI `ChatModel` 适配，业务层不再向图状态或 SSE 暴露 Spring AI 响应对象。
- 标题、配图分析 Agent 改为通过端口请求结构化结果；大纲和正文 Agent 改为消费端口的文本流，原有 SSE 消息类型及聚合逻辑保持不变。
- 新增无 Spring Context、无网络的 Fake 端口契约测试，固定普通调用、分块流和结构化反序列化的调用行为与 prompt 传递。
- 使用 Microsoft OpenJDK `21.0.12.1` 执行 `mvn test` 通过，共 5 项测试（含既有图契约与 OpenAI 客户端装配测试）；LiteLLM 真实调用仍只由显式 smoke profile 覆盖。
- 此项仅完成 U2.1 的项目边界与 Fake 验证；checkpoint/resume、Supervisor/Skill、Policy Gateway 异步 Tool 等 U2 门禁仍未完成，版本尚不冻结。

## 2026-08-25  P0-U：流事件 DTO 与 OpenAI Mock HTTP 契约

- 新增框架无关的 `AgentStreamEvent`（任务、节点、事件类型、序号、增量、时间），大纲、正文和并行配图均通过该 DTO 发布；`AgentStreamEventMapper` 只在编排器至既有 SSE 管道的边界转换为原字符串协议，前端报文不变。
- `StreamHandlerContext` 支持捕获可跨异步线程使用的事件发布器，并共享序号计数，避免并行图片任务依赖子线程的 `ThreadLocal`。
- 使用本地 `MockWebServer` 增加 `SpringAiModelAdapter` 契约：验证 OpenAI 兼容普通聊天 JSON、SSE 两段文本的顺序输出与 400 错误向调用方抛出。测试不访问 LiteLLM、数据库或图片服务。
- Mock 场景的 `RetryTemplate` 显式限制为一次尝试。429 等可重试状态的退避、上限及审计尚未定义，留待 Policy Gateway 与异步 Tool 门禁统一实现，不能依赖框架默认重试。
- 使用 Microsoft OpenJDK `21.0.12.1` 执行 `mvn test` 通过，共 9 项测试。U2.1 与 U2.2 已完成；下一项为 Supervisor/Routing 契约，版本仍暂不冻结。

## 2026-08-25  P0-U：Supervisor 路由与受限并发 Fixture

- 新增项目自有的 `SupervisorPlan`、`SubtaskSpec`、`ExecutionRoute` 与 `SupervisorScheduler`，将“是否先研究”、最大 Writer 并发和章节任务从自由字符串明确为强类型计划字段。
- `SupervisorSchedulerContractTest` 使用 Fake Writer 验证：研究任务路由到 `RESEARCH`，非研究任务路由到 `WRITE`；三个子任务在上限 2 下运行，实际活动任务数不超过 2，最终按 `sectionIndex` 稳定归并而非按完成顺序返回。
- 实现仅作为候选依赖的隔离 Fixture，未接入当前标题—大纲—正文业务图，也不声称已完成业务 Supervisor。
- 使用 Microsoft OpenJDK `21.0.12.1` 执行 `mvn test` 通过，共 11 项测试。下一项为 Skill Registry 的渐进加载与权限边界；checkpoint/resume、Policy Gateway 异步 Tool 仍未验证，版本继续暂不冻结。
## 2026-08-30 11:50 — P3 E6.4A 图片继续边界与恢复输入

- 新增 `ApprovedContentImageExecutor` 及 `LegacyApprovedContentImageExecutor`，将质量通过后的交付限制为 `image_analyzer → parallel_image_generator → content_merger`；该端口只接收已确认 Markdown、标题、风格和图片方法，不能调用正文生成器。
- `ArticleAgentOrchestrator` 新增独立 image-only StateGraph，保留旧 phase3 图和默认路径不变；图片完成事件只在图片图成功后产生。
- 实施中发现 E6.3 checkpoint 原先仅有 Markdown，无法在不回读可变文章记录的前提下恢复图片交付。已先更新 E6.4A 执行文档，并将 allow-list snapshot 扩展为稳定的 delivery context（标题、风格、图片方法）；明确不保存 Prompt、Review 或来源正文。
- 定向验证：`P3ContentCheckpointCodecTest`、`P3ContentRecoveryAdapterTest`、`ArticleWorkflowRunnerTest`、`LegacyApprovedContentImageExecutorTest` 共 9 项通过。Maven 的 Windows 编译器资源关闭偶发错误经原命令重试后消失。

## 2026-08-30 11:56 — P3 E6.4A 继续交付服务与兼容接口

- 新增 `ContentQualityContinuationService`：只可领取 `content-quality-accepted` READY checkpoint；图片结果通过持久化 node execution 保存，以便失败后的继续请求复用结果。
- 图片完成后以质量版本为父版本发布 append-only 最终交付版本与图片 Artifact，再回填旧 `Article` 的内容/图片字段，最后才同步 Agent Run 为 `COMPLETED`。
- 新增 `POST /api/agent-runs/{runId}/content-quality/continue`；先复用文章详情权限校验，旧文章 HTTP 接口不变。
- 最终默认回归：`mvn test` 共 74 项通过，0 失败、0 错误、0 跳过。
- `mvn test -Ppersistence-integration -Dtest=AgentCheckpointPersistenceIntegrationTest` 未构成验收：Docker Desktop Linux engine 命名管道不存在，Testcontainers 13 项全部跳过。Docker 守护进程恢复后必须复跑该命令并取得真实执行结果。
- `AgentRunControllerTest` 已覆盖无请求体 continue 调用、响应结构与文章权限校验前置，定向通过。
