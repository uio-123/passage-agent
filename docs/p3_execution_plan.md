# P3：并行写作与多 Agent 评审闭环执行计划

> 状态：`STAGING`；E1–E6 实现与持久化验证已完成，主流程默认关闭并等待真实任务验收。
> 日期：2026-08-29
> 依据：[plan.md](plan.md)、[p2_execution_plan.md](p2_execution_plan.md)、[p2-e5_execution_plan.md](p2-e5_execution_plan.md) 与当前代码。

## 1. 目标与边界

P3 在 P1 的 Run/checkpoint/幂等底座和 P2 的 `ResearchBundle`/Skill 契约之上，交付章节级并行写作、质量评审、有限返工与最终 Artifact。Workflow 决定并发、归并、质量门和循环上限；Writer、Reviewer 与 Revision Agent 只输出结构化结果，不能直接调用 Tool、修改 Run 或绕过来源引用。

真实受注册 Search Tool 与框架 `read_skill` 兼容验证不阻塞 P3 的离线契约；P3 初期仅消费 P2 已有的固定/回放 `ResearchBundle`。

## 2. 分步实施

### E1：章节写作与引用交接契约

- 新增 `SectionTask`、`SectionWriterRequest`、`SectionDraft` 与 `SectionWriter` 项目 DTO/端口。
- 引用仅按 P2 `ResearchSource.sourceId` 交接；任务声明的必需来源必须存在于 Bundle，Draft 不能引用未知、重复或未按任务要求使用的来源。
- 新增稳定 fan-in：按 `sectionIndex` 而不是完成顺序归并，拒绝重复章节任务/草稿。
- 验收：完全离线测试覆盖引用完整性、失败拒绝与稳定归并；不改现有文章主图或数据库。

### E2：受限 fan-out/fan-in 与 child run 接入

- 将受验证 SectionTask 映射到 child run 和已有节点幂等服务，按 Workflow 状态控制最大并发和失败重试。
- 明确章节全部成功/允许局部降级语义，持久化可恢复的章节结果；不得由 CompletableFuture 自行绕过 checkpoint。

### E3：双 Reviewer 与 Quality Gate

- 并行执行 Fact Checker 与 Style Reviewer，输出结构化问题、定位、分数和建议；Fact Checker 只能使用 Writer 引用 ID 和 ResearchBundle。
- Quality Gate 用代码验证输出并限制最多两轮返工，拒绝无限循环或 Reviewer 自行重写全文。

### E4：局部 Revision 与版本化 Artifact

- Revision Agent 只修改被 Gate 标记的章节；持久化版本链、修改原因和稳定全文归并。
- Chief Editor 生成带 Markdown、来源、图片、质量报告及哈希的 Artifact Manifest。

### E5：集成收口

- 为 fan-out/fan-in、部分失败、两轮终止、恢复重试和 Artifact 完整性补充 Testcontainers/图集成测试。
- 记录受控的串行/并行耗时证据，不将开发机偶发时间表述为性能结论。

## 3. 回滚与验证

- E1 仅新增框架无关契约，可删除调用方并回到现有 Supervisor Writer 字符串回调；不改数据表。
- 每个 E 结束前运行 `git diff --check` 与默认 `mvn test`；涉及 Run/数据库时再运行 `persistence-integration`。

## 4. E1 实施结果

- 新增 `SectionTask`、`SectionWriterRequest`、`SectionDraft`、`SectionWriter`、`SectionDraftValidator` 与 `SectionFanIn`。章节任务以来源 ID 声明证据要求，Writer Draft 只允许引用 Bundle 中已有来源；缺失必需引用、伪造引用、任务错配及重复章节均被拒绝。
- E1 不改变现有文章 StateGraph、Run 持久化或数据库表；后续 E2 将在该契约外部接入受限 fan-out/fan-in 与 child run。
- `SectionWritingContractTest` 覆盖引用交接、失败拒绝和与完成顺序无关的稳定归并。

## 5. E2 实施结果

- 新增 `ParallelSectionWritingUseCase`，复用 `SupervisorScheduler` 的有界并发与稳定顺序；每个 `SectionTask` 以稳定 UUID 创建/复用 writer child run，并将 Writer 置于 `AgentNodeExecutionService` 的 `section-writer` 幂等节点内。
- 成功草稿序列化为节点快照；同一 child run、节点和 state version 的重试直接反序列化首次草稿，不会重新调用 Writer。当前 E2 语义为章节全部成功，部分成功降级将在 E3 Quality Gate 一并确定。
- 离线 `ParallelSectionWritingUseCaseTest` 覆盖 child run、稳定归并和快照复用；Testcontainers MySQL 用例覆盖重试只产生一个节点执行记录和一次 Writer 调用。

## 6. E3 实施结果

- 新增 `FactChecker`、`StyleReviewer`、`ReviewReport`、`ReviewIssue` 与 `ParallelSectionReviewUseCase`。Fact Checker 输入强制为 Draft 引用 ID 加 ResearchBundle，未知来源 ID 在调用前拒绝；两个 Reviewer 并行执行但不能输出修改后的章节。
- 新增代码拥有的 `QualityGatePolicy` / `QualityGate`：默认 Fact 与 Style 分数均至少 80 且无 blocker 才接受；失败时最多请求两轮 Revision，第二轮后仍失败则明确终止。
- `QualityGateContractTest` 覆盖通过、两轮上限与伪造来源拒绝；E3 尚未写入 Run/数据库，E4 才将 Gate 决定接入局部 Revision 与版本化 Artifact。

## 7. E4 实施结果

- 新增受限 `SectionRevisionUseCase` 与 `RevisionAgent` 输入契约。Revision Agent 只能收到某一被 Gate 标记章节、该章节任务的来源 Bundle 和同章节问题；Gate 之外的章节不会进入 Agent 输入，也会在稳定归并时保留原 `SectionDraft` 实例。
- 新增不可变 `ArticleVersion` / `ArticleVersionChain`：首稿必须为版本 1，后续版本只能追加并显式指向前一版本，保留修改原因与时间。当前是领域层 append-only 链，持久化适配器及版本查询 API 留待 E5 的 Run/Artifact 集成，避免预设尚未确认的文章表结构。
- `ArticleArtifactManifestFactory` 基于稳定正文、来源 ID 和 Gate 结果生成 Markdown、来源包、质量报告三项虚拟存储引用及 SHA-256；图片 Artifact 要待既有图片结果正式接入后再登记，不能伪造空图片产物。
- `SectionRevisionContractTest` 覆盖局部改写、未标记章节字节级保留、非法 Gate 目标/非 `REVISE` 决定拒绝、版本链追加和 Manifest 哈希。

## 8. E5 阶段结果：版本与 Artifact 持久化

- 新增 `agent_article_version` 与 `agent_artifact` 迁移、Compose 初始化挂载以及 `AgentArticleArtifactService`。版本以 `(runId, version)` 唯一，Artifact 以 `(runId, articleVersion, artifactId)` 唯一；服务只允许首稿或紧邻已持久化父版本的追加，拒绝跳号和既有版本/Manifest 被不同内容覆盖。
- MySQL `JSON` 会规范化文本格式，因此重试复用以 JSON 结构等价比较章节快照，不能错误地按原字符串拒绝相同版本。该问题已由 Testcontainers 实测暴露并修正。
- `AgentCheckpointPersistenceIntegrationTest` 新增版本链/Manifest 用例；当前持久化集成共 10 项、0 失败、0 错误。E5 其余图级“两轮 Gate 终止、部分章节失败”集成仍待主 Workflow 将 E3/E4 编排为一条受 checkpoint 控制的路径后补齐，不能在当前独立 UseCase 上伪称完成。

## 9. E5 阶段结果：受限质量闭环

- 新增 `QualityRevisionWorkflowUseCase`，串接持久化 Writer fan-out、双 Reviewer、代码拥有的 Quality Gate、局部 Revision、版本链和 Artifact 发布。每一轮只将 Gate 问题对应章节交给 Revision；`REJECT_MAX_ROUNDS` 不再执行 Revision 或发布新版本。
- Revision 以 `section-revision-{sectionId}`、Artifact 以 `article-artifact-v{version}` 进入既有 `AgentNodeExecutionService`；快照严格写 JSON，避免 MySQL `JSON` 列拒绝纯文本结果。首次集成测试据此发现并修正了发布节点快照格式。
- Testcontainers 现为 13 项、0 失败、0 错误，覆盖“首轮失败 → 局部修订 → 通过”、同 run/state 重试不重复 Writer/Revision/Artifact、两条版本记录与三类 Artifact、两个 Revision 回合后的明确拒绝（不发布被拒绝的第三版），以及任一 Writer 失败时不评审、不发布、恢复后仅重跑失败章节。
- 已确认产品语义为“不允许部分交付”：失败章节会保留 `FAILED` node execution，已完成章节保留 `SUCCEEDED` 快照；再次执行同 run/state 时只重试失败章节。该闭环以现有 checkpoint-compatible 节点幂等边界实现；将它替换进旧 `StateGraph` 文章主图属于单独主流程迁移，不在本 E5 混入。
