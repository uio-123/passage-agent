# 开发记录

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
