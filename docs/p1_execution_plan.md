# P1：Supervisor、条件路由与可恢复执行计划

> 状态：`DONE`（E1–E5 计划范围已完成）；P1 阶段整体因 Supervisor/HITL 扩展项仍标记为 `STAGING`
> 日期：2026-08-28
> 依据：[plan.md](plan.md)、[development_log.md](development_log.md)、[p0-b_execution_plan.md](p0-b_execution_plan.md)、当前代码与默认测试。

## 1. 范围与不可变约束

P1 把 P0-B 已完成的单一阶段路由图扩展为可治理、可持久化、可恢复的业务执行链路。本阶段交付 Supervisor 接入、确定性条件路由、父子 Run 记录、checkpoint、恢复互斥和节点幂等；不交付真实网页研究、Skill Registry、Policy Gateway、外部 Tool 的统一超时/预算/审计、质量返工或前端 DAG。

- 维持 Java 21、Spring AI Alibaba `1.1.2.2`、Spring AI `1.1.2` 的冻结版本；不在 P1 混入依赖升级。
- 保持既有文章 API、SSE 消息、标题/大纲 HITL 审批流程，以及 `article`、`agent_log` 表兼容。
- `WorkflowRunner` 与 typed `WorkflowState` 继续是业务调用边界；Spring AI `StateGraph` 类型仅留在 `agent/graph` 适配层。
- Supervisor 的开放式规划结果必须转换为受校验的强类型计划；确定性业务规则优先代码路由，不能把恢复/幂等判断交给模型。
- checkpoint 只在节点成功和状态快照持久化后推进；同一 `runId + nodeId + stateVersion` 的有效外部副作用最多一次。
- 同一 checkpoint 同时只允许一个恢复者取得推进权；终态 Run 不可回退；取消优先于继续执行。
- 每个增量都有离线测试。涉及数据库一致性的测试使用 Testcontainers；若 Docker 不可用，不把单元测试替代成“已验证持久化”，而是记录阻塞。

## 2. 已完成前置基线

| 能力 | 当前证据 | P1 中的作用 |
|---|---|---|
| typed 状态、Reducer、Runner 与统一入口图 | `agent/state`、`agent/workflow`、`agent/graph` | checkpoint 状态和节点执行边界 |
| `AgentRun` 与 `agent_run` 基础表 | `AgentRunService`、`sql/add_agent_run_tables.sql` | 根/子 Run 持久化骨架 |
| Supervisor 强类型 Fixture | `agent/supervisor`、`SupervisorSchedulerContractTest` | 路由和稳定 fan-in 基线，尚未接入业务 |
| MemorySaver interrupt/resume 示例 | `CheckpointCompatibilityTest` | 仅 API 隔离证据，不能替代持久化恢复 |
| 25 项无网络回归 | `mvn test`（P0-B 收口记录） | P1 的回归底线 |

## 3. 顺序执行与验收

### E1：持久化模型与并发原语

- 目标：为 checkpoint、节点尝试和幂等副作用建立最小持久化模型；明确状态版本、乐观推进条件和唯一键。
- 修改范围：增量 SQL、`AgentRunRecord`/Mapper/Service、必要的持久化 DTO；不改变现有文章 API。
- 验收：能原子写入状态快照与 checkpoint；同一 checkpoint 只有一个恢复者可获锁；重复相同节点尝试/副作用键不产生第二条有效记录。
- 测试：Testcontainers MySQL 覆盖父子 Run、乐观更新、并发恢复、重复提交与终态保护。
- 回滚：新表/索引和新服务只由 P1 路径使用；停用 P1 恢复入口即可回退到 P0-B 行为，不删除历史 `agent_run` 数据。
- 实际结果（已完成）：已新增 `stateVersion`、`agent_checkpoint` 与 `agent_node_execution` 的迁移；checkpoint Mapper/Service 以状态和版本条件更新实现单恢复者领取，父子 Run 已可持久化创建。`AgentNodeExecutionService` 以唯一执行键领取节点，成功结果直接复用、并发重放被拒绝、失败状态可条件重试。`persistence-integration` Profile 的 Testcontainers MySQL 共 4 项测试，验证迁移、版本推进、双恢复者竞争、节点结果复用、并发重放拒绝和失败后重试；E1 验收通过。

### E2：Supervisor 业务计划与条件路由

- 目标：把既有 Fixture 接入业务工作流，先支持“是否研究（当前仅跳过或占位）”与“写作”两条受限路线，并为后续配图/返工保留受校验路由点。
- 修改范围：Supervisor 适配器、工作流图、typed state、子 Run 映射与离线 Fake 测试；不引入真实研究 Tool。
- 验收：计划中的非法依赖、预算或 Tool 权限被拒绝；路由矩阵可证明研究请求不会误走写作直通、免研究请求不创建研究子 Run；fan-out 结果按稳定业务顺序归并。
- 回滚：保留现有标题/大纲/正文兼容路由；关闭 Supervisor 入口后仍可完成既有三阶段工作流。
- 实际结果（已完成）：`SupervisorPlan` 已显式包含子任务预算和允许的 Tool 集合，`SubtaskSpec` 已包含依赖与所需 Tool；`SupervisorPlanValidator` 会在路由前拒绝超预算、空/重复子任务 ID、非法或循环依赖及未授权 Tool。`SupervisorWorkflowService` 复用既有 Scheduler：研究请求会先创建独立研究子 Run 并执行占位节点，免研究请求不会创建该子 Run；Writer 仅在依赖完成后按波次、受并发上限执行，结果按业务章节顺序归并并各自映射独立子 Run。离线测试覆盖两条路由、依赖波次、稳定归并与计划拒绝。真实研究 Tool、来源和 Supervisor 的模型规划仍留在 P2。

### E3：checkpoint、HITL 恢复与取消

- 目标：在标题选择、大纲确认与节点完成边界保存可恢复快照，提供服务层恢复和取消能力。
- 修改范围：checkpoint 序列化/反序列化、Run 状态流转、恢复/取消服务及 API；必要时新增安全的数据库迁移。
- 验收：进程重建后可按 `runId` 继续到下一个未完成节点；已完成节点不重放；HITL 决策只消费一次；取消任务不能再次被恢复。
- 测试：持久化恢复、两恢复者竞争、HITL 重复提交、取消与恢复竞争；不访问模型或图片服务。
- 回滚：保留 checkpoint 和 Run 数据；暂停恢复入口并按现有阶段 API 继续人工操作。
- 实际结果（已完成）：新增 `WorkflowRecoveryService` 作为不暴露图框架类型的恢复边界。它只在领取 checkpoint 后执行下一个节点，成功才消费 checkpoint，执行异常则释放领取权以便安全重试。取消使用 Run 状态的条件更新，随后将 READY/CLAIMED checkpoint 置为 CANCELLED；恢复抢占若发现 Run 已变化会释放领取权，避免遗留 CLAIMED 状态。离线测试覆盖消费、失败释放和取消传播；Testcontainers MySQL 5 项覆盖取消后不可恢复。

### E4：节点幂等与 Tool 副作用一致性

- 目标：将节点幂等键与图片生成/上传这类外部副作用的提交记录关联，处理“Tool 已完成但 checkpoint 尚未提交”的重试。
- 修改范围：节点执行网关、外部副作用记录/适配边界、图片路径的 Fake Tool 契约与持久化测试；P2 才统一 Tool 重试、超时、预算和审计策略。
- 验收：同一幂等键重复执行只返回首个已提交结果；模拟 checkpoint 写入失败后的重试不会重复产生图片/上传/配额副作用；未提交的副作用可被安全识别并补偿或复用。
- 测试：Testcontainers + 可计数 Fake Tool 覆盖重复请求、节点重放、checkpoint 提交失败及恢复重试。
- 回滚：禁用新的节点网关不会删除副作用记录；只允许人工修复未完成 Run，不自动再次调用 Tool。
- 实际结果（已完成）：新增 `IdempotentImageGenerationGateway` 并接入生产路径的 `ParallelImageGenerator`。存在 Agent Run 时，图片副作用以 `runId + image nodeId + stateVersion` 领取执行权；已成功记录直接反序列化并复用首次图片结果，只有旧请求（无 Run）保留直连兼容路径。图片 Tool 返回失败不会被伪记为成功。Testcontainers MySQL 增至 6 项，模拟“图片已成功但 checkpoint 未推进”的重试，验证第二次请求不再调用 Tool 且返回首次 URL。

### E5：P1 收口与交接

- 目标：核对路由、持久化、恢复、取消和幂等不变量，更新项目文档。
- 验收：默认离线回归通过；数据库集成测试在 Docker 可用时通过；`README.md`、`plan.md`、开发日志和 SQL/Compose 部署说明一致；明确 P2 尚未完成的 Tool 治理范围。
- 回滚：仅文档和测试调整可直接回退；运行时功能按 E1–E4 的独立开关/入口回退。
- 实际结果（已完成）：`README.md` 已同步持久化迁移、Docker Testcontainers Profile 与 P1 的已实现边界；Compose 已加载两份 P1 SQL。默认 `mvn test` 通过 36 项；`mvn -Ppersistence-integration test` 的 MySQL/Testcontainers 测试通过 6 项；`git diff --check` 无空白错误。P2 仍负责真实研究、来源、统一 Tool 策略和审计；P1.5 再独立处理项目身份迁移。

## 4. 阶段门禁

每个 E1–E5 完成前：

1. 运行 `git diff --check`，无空白错误。
2. 运行 `mvn test`；记录测试数量、失败数与错误数。首次 Windows 增量编译遇到已知资源关闭问题时，只能在无源码改动后重跑。
3. E1、E3、E4 还必须执行对应 Testcontainers 测试；Docker 未启动或镜像不可用时，保留待验证状态，不能标为完成。
4. 数据库结构变化同步 `sql/`、Docker 初始化脚本、README；实质设计决策同步 `plan.md` 与 `development_log.md`。
5. 不以 MemorySaver、单元 Mock 或模型成功返回替代持久化/副作用一致性证明。

## 5. 本次校验结论

- P0-B 的统一图只实现进程内按入口字符串的条件路由；没有 Supervisor 业务接入、状态快照写入或恢复入口，符合 E1–E4 尚未开始的判断。
- `agent_run` 已预留 `checkpointId` 与 `stateSnapshot`，但服务仅同步状态和当前节点；尚无版本条件更新、子 Run 创建、恢复互斥或幂等记录，因此需要先执行 E1。
- `SupervisorScheduler` 已覆盖确定性初始路由、并发上限和稳定归并，但没有业务图或数据库依赖；E2 必须复用其规则而非另写一套调度器。
- 当前默认回归基线为 25 项；先执行 E1 的持久化模型与测试，再进入业务 Supervisor 和恢复接口。
