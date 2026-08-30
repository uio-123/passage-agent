# P4 收口修正执行计划

状态：执行中  
目标：使 P4 计划、实现、测试与 Git 历史一致，并为 P5 提供可信的运行测量基线。

## 阶段与顺序

| 阶段 | 范围 | 完成证据 |
|---|---|---|
| C1 | 对齐 `docs/plan.md` 的原 P4 条目，区分已实现、部分实现和未实现 | 无未证实的 `[x]`；每个保留项链接到代码/测试或明确后续阶段 |
| C2 | 可信模型调用测量：模型名、输入/输出 Token、首 Token、总耗时、重试次数与安全错误码 | 仅采集模型客户端实际返回的 usage/时钟；无 usage 时明确 `UNKNOWN`，不由正文长度估算 |
| C3 | Testcontainers/MVC 验证 Event 持久化、单 run 序号、`Last-Event-ID` 补发和授权/脱敏 | Docker 可用 Profile 真实验证；默认测试覆盖 API 输入边界 |
| C4 | 分层整理并提交 P2、P3、P4 | 仅包含相关改动；提交前完整默认回归与前端构建通过 |

## 实施记录

- C1 已完成：P4 原始条目已改为可验证的已实现/部分实现陈述。
- C2 已完成基础采集：模型 response metadata 的 usage/模型名、总耗时、流式首 Token、显式 `retryCount` 进入 append-only `agent_model_call_metric`；缺失 usage 保持 NULL，成本移交 P5。
- C3 已完成：宿主 Docker 的 `AgentCheckpointPersistenceIntegrationTest` 14 项、0 失败、0 错误、0 跳过。测试暴露并修复了连接池下首次事件读到旧 `LAST_INSERT_ID()` 的序号错误，现从每 run 的持久化计数行读取序号。

每一 C 阶段在实施前更新本文件的接口、迁移、测试与回滚说明；不跨阶段偷偷引入未验证的指标或重构。

## C1 对齐准则

- `已实现`：存在生产代码、可达入口与相应验证；默认关闭的旁路能力必须标注开关和部署前置条件。
- `部分实现`：存在安全投影/基础设施但缺少原承诺的数据源或用户交付，保留未完成标记，不得替换为 `[x]`。
- `未实现`：不创建占位接口；明确移入 P5 或后续阶段。

## C2 数据边界

- 只从 `AiModelPort` / 模型响应的实际 usage 元数据采集 Token、模型名和首 Token 时间；业务 Agent、网页读取和图片服务不得伪造这些字段。
- 记录不可逆的聚合/受控字段，禁止落 Prompt、完整 response、认证信息、Cookie、网页正文或用户私密内容。
- 成本只有在模型价格表版本、币种、单位及输入/输出 Token 都已知时才可计算；否则为 `UNKNOWN`。
- 运行、节点、Tool 三类数据按独立记录或可追溯关联保存；C2 不宣称已经涵盖无模型调用的旧节点。

### C2 实施接口与迁移

- 新增 `ModelCallMeasurement` 项目 DTO，记录 `runId`、阶段、调用类型、模型名、输入/输出/总 Token（可空）、首 Token/总耗时（可空）、尝试号、实际 retryCount、受控错误码及时间；Token/成本的未知值为 `null`，不是 `0`。当前端口无自动重试器，所以每次调用可靠记录 `retryCount=0`，后续统一重试器接入前不得以调用总数推断重试。
- 新增 append-only `agent_model_call_metric` 表。唯一键为 `runId + stage + attempt + callIndex`；其用途是运行审计与 P4 UI，不替代 `agent_log`。
- 扩展 `WorkflowMetricsCollector` 的活跃 stage scope 以接受一次模型调用的实际测量；`MetricsCollectingAiModelPort` 只负责计时/失败计数，Spring AI Adapter 从 `ChatResponse` usage 和 metadata 提取模型/Token。
- 流式调用首 Token 在订阅后的第一条非空 chunk 记录；若下游未返回 usage，仍保存模型名和时延，但 Token/成本保持未知。
- C2 只保存 metrics，不写价格。价格表/成本计算属于 P5，避免在没有版本化价格来源时输出伪成本。

## C3 测试范围

- Testcontainers MySQL：并发或连续写入事件的唯一 `(runId, sequence)`、严格读取顺序、持久化 payload 脱敏。
- MVC：非负 `afterSequence`、非法 `Last-Event-ID`、文章权限校验、SSE snapshot 先于 replay、事件 id 等于 sequence。
- 事件发布失败不得影响 run/checkpoint 状态。

## C4 Git 边界

按 P2、P3、P4（含收口修正）拆分提交；先检查每个待提交文件的归属，不将 `target/`、`frontend/dist/`、环境文件或无关用户改动加入。只有所有验证通过后提交，不推送远程。
