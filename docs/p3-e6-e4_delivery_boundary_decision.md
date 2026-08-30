# P3 E6.4：质量通过到图片交付的流程决策

> 状态：需要产品决策；在决定前不接入图片后半段。

## 已确认事实

- E6.3 在 P3 Gate `ACCEPT` 后将 Run 持久化为 `WAITING_FOR_APPROVAL`，并创建可恢复 checkpoint。
- 旧 HTTP 流程只有一次 `generateContent` 调用；没有“从 quality accepted checkpoint 继续图片”的独立接口。
- 旧 phase3 的图片节点与 ContentGenerator 同图，需要先拆出图片后半段；即使拆出，仍必须定义它由谁、何时触发。

因此，不能在当前 `generateContent` 的 P3 分支中立即执行图片：这会绕过刚创建的审批/checkpoint 边界，也无法验证断线或人工拒绝后的恢复语义。

## 选项

### A. 质量通过后要求用户确认（建议）

新增 `POST /api/agent-runs/{runId}/content-quality/continue`。客户端在收到可忽略的质量通过进度后展示预览；用户确认才由该接口通过 `WorkflowRecoveryService.resume` 领取 checkpoint、运行图片后半段并完成文章。

- 优点：与 `WAITING_FOR_APPROVAL` 和 P1 checkpoint 语义一致；用户可在图片调用前审阅正文；取消/恢复清晰。
- 代价：增加一个向后兼容的新接口和前端后续按钮；旧接口不变。

### B. 自动继续图片

不创建 `WAITING_FOR_APPROVAL` checkpoint，或在同一次调用中立即 claim/consume 后执行图片。

- 优点：旧客户端表面行为最接近原来。
- 风险：质量 checkpoint 缺少可观察的恢复/审批意义；必须重新设计 Run 状态与崩溃窗口，不能复用 E6.3 文档的验收。

## 推荐的执行顺序（选择 A 后）

1. 拆分只含图片分析、图片生成、合成的图和 `ApprovedContentImageExecutor`。
2. 新增 continue HTTP service/controller，输入仅 runId；从 claimed checkpoint 解析 Markdown，调用图片端口，成功后消费 checkpoint/完成 Run。
3. 默认与 Testcontainers 覆盖：未确认不调用图片、确认一次最多一次、两个 continue 竞争只有一个成功、取消优先、旧 `generateContent` 接口保持不变。

## 回滚

关闭 P3 flag。已存在 `WAITING_FOR_APPROVAL` P3 run 保留，不能静默转入旧图；由显式取消或 P3 continue 路径处理。
