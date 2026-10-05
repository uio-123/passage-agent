# P3 E6：文章主流程质量闭环迁移执行计划

> 状态：`STAGING`；Runner 分支、质量继续接口与独立图片图已接入，`article.agent.quality-loop.enabled` 默认仍为 `false`。
> 前置：P1 checkpoint/幂等、P2 ResearchBundle、P3 E1–E5 与四个生产模型适配器。

## 1. 目标与边界

标题和大纲审批保持原样。用户确认大纲后，正文可在 feature flag 控制下走 P3 章节写作、双 Reviewer、最多两轮局部 Revision、版本/Artifact；只有 `ACCEPT` 才进入图片和完成状态。不得重写既有三张 StateGraph、修改旧 `article` 表语义、伪造图片 Artifact，或把 Review/网页正文发送到 SSE。

## 2. 已确认配置和回退

| 项目 | 决策 |
|---|---|
| flag | `article.agent.quality-loop.enabled` |
| 默认值/范围 | `false`；首版只允许后端全局启用 |
| flag 关闭 | `generateContent` 完全走 `ArticleWorkflowExecutor.executeContent`；不创建 P3 child run、版本或 Artifact |
| 免研究 | Fact Checker 返回 `FACT_ENHANCEMENT_NOT_REQUESTED` 的 MINOR 审计，不阻断、不伪造引用 |
| Writer 失败 | 不部分交付：不评审、不发布、不执行图片；成功章节快照保留，恢复只重试失败章节 |
| 回退 | 关闭 flag；未进入 P3 的新 run 立即走旧图，已进入 P3 的 run 按其已持久化模式恢复，禁止同一 run 静默换模式 |

版本/Artifact 均只追加，回退绝不删除或覆盖历史。

## 3. 已审批大纲到 `SectionTask`

入口要求 `WorkflowState.draft.selectedTitle`、`outline` 均非空。`ApprovedOutlineWritingRequestFactory` 按确认顺序映射：

| Outline | P3 | 规则 |
|---|---|---|
| `sections[i]` | `sectionIndex=i` | 0 起、稳定顺序 |
| `sections[i]` | `id=outline-section-{i}` | 不随标题编辑改变 |
| `title` | `heading` | 空值拒绝 |
| `points` | `instruction` | 确定性拼接；不从文本提取 URL |
| P2 Bundle | `requiredSourceIds` | 首版为空；后续 Evidence Planner 才可显式填充 |

无研究时生成含 `Fact enhancement was not requested` unresolved claim 的空来源 Bundle，`factEnhancementAvailable=false`；它不表示任何事实已经验证。

## 4. P3 回填旧状态与图片位置

`QualityWorkflowResult` 只有 `ACCEPT` 时回填：所有 `SectionDraft.markdown` 依 `sectionIndex` 用两个换行连接，调用 `WorkflowStateReducer.withContent` 写入 `draft.content`。版本号、Gate 和 Manifest 只留在 P3 表。

| P3 结果 | Runner 行为 |
|---|---|
| `ACCEPT` | 回填 content；之后复用现有 Image Analyzer / ParallelImageGenerator / ContentMerger |
| `REJECT_MAX_ROUNDS` | 不回填、不调用图片，返回新的可恢复 `QUALITY_REJECTED` 错误 |
| Writer/Revision/模型失败 | 不回填、不调用图片，保留 checkpoint/node 失败，返回可恢复 `QUALITY_EXECUTION` |

图片明确在 P3 之后。若旧图片节点依赖旧完整正文格式，新增显式 Markdown adapter；仅真实图片结果可追加最终 Artifact，不能填写空图片记录。

## 5. 分步接入

### E6.1（已完成）

typed approved-outline 请求、共享模型适配器、默认关闭 flag 和离线契约测试；不改主图。

### E6.2：Runner feature-flag 分支

- `generateContent` 审批校验后分支：关闭调用旧图；开启只调用 `P3ContentWorkflowAdapter`，禁止旧 ContentGenerator 与 P3 Writer 双写。
- Adapter 只接收 typed request/result，不处理 HTTP、SSE 或 `ArticleState`。
- 增加 `QUALITY_REJECTED`、`QUALITY_EXECUTION` 错误码；非成功绝不转 `COMPLETED`。

### E6.3：SSE、checkpoint、恢复与 HTTP

- 保持旧 HTTP 请求/响应和完成 SSE 可消费；新 P3 进度事件可选且带可忽略前缀，只含 runId/阶段/章节 ID/轮次/摘要，不能含 Prompt、网页正文、密钥或完整 Review。
- P3 分支入口和 Artifact 成功后持久化 P1 checkpoint；stateVersion 只由 Run/checkpoint 发放，不能以 Revision 轮次替代。
- 旧恢复接口重入同一 adapter；节点幂等复用 Writer/Revision/Artifact，取消优先。

### E6.4：图片和最终交付

- Gate 接受后才调旧图片链；图片失败沿用既有降级策略。
- 成功后才完成 Run；拒绝/失败绝不调用 Image Tool、Merger 或完成状态。

## 6. 验收矩阵

| 层级 | 场景 | 必须断言 |
|---|---|---|
| 默认 | flag 关闭 | 仅旧图；无 P3 child run/version/artifact |
| 默认 | flag 开启 + ACCEPT | 稳定 tasks、Markdown 回填、再进入图片适配 |
| 默认 | 未审批/空 outline | 不调用模型、P3、图片 |
| 默认 | 免研究 | Fact 信息审计，不因无来源失败，无伪引用 |
| 默认 | Gate 拒绝/Writer 失败 | 不回填、无图片/完成、错误可恢复 |
| 默认 | SSE 兼容 | 旧完成事件不变；新事件无敏感数据 |
| Testcontainers | Writer/Revision/Artifact 后 checkpoint 中断 | 恢复后副作用最多一次 |
| Testcontainers | 一章失败后恢复 | 成功章节复用、失败重试、无部分发布 |
| Testcontainers | 两轮拒绝 | 不发布拒绝版本或图片 |
| Testcontainers | flag 关闭 | 无 P3 持久化记录 |

每步执行 `git diff --check` 与 `mvn test`；涉及数据库/恢复时执行 `mvn test -Ppersistence-integration -Dtest=AgentCheckpointPersistenceIntegrationTest`。任何旧 HTTP/SSE 兼容失败、P3/旧图双写、恢复可能重复 Artifact/图片副作用时立即停止、保持 flag 关闭并修复。
