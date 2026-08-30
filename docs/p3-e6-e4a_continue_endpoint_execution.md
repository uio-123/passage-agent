# P3 E6.4A：质量确认继续图片接口执行清单

> 状态：执行中（continue service 与 HTTP 接口已接入；测试与 Testcontainers 验收待完成）
> 决策：采用 quality-continue 接口；旧文章接口保持不变。

## HTTP 契约

`POST /api/agent-runs/{runId}/content-quality/continue`

- 无请求体；当前版本不新增前端强制字段。
- 仅接受属于该 run 的 `content-quality-accepted` READY checkpoint。
- 成功返回 `{runId, stage: ARTICLE_COMPLETED, fullContent}`；失败沿用项目 `BaseResponse`/全局异常格式，不暴露 checkpoint 内容、Prompt、Review 或来源正文。
- 重复确认、并发确认或已取消 run 由 `WorkflowRecoveryService.resume` 的 CAS 领取规则拒绝/串行化；不重新执行 P3 Writer/Reviewer/Revision。

## 运行实现

1. 新增 `ApprovedContentImageExecutor` 端口与只包含 `image_analyzer → parallel_image_generator → content_merger` 的 Graph；输入为已接受 Markdown/标题/风格/图片方法，禁止包含 ContentGenerator。
2. 扩展 E6.3 allow-list snapshot，持久化图片交付所需且已审批的稳定元数据：主/副标题、文章风格、已选择的图片方法。恢复禁止从可变的旧 `Article` 记录回读这些字段；快照不包含 Prompt、Review 正文、来源正文或任何模型中间状态。
3. 新增 `ContentQualityContinuationService`：查询/领取 checkpoint，使用 `P3ContentRecoveryAdapter` 恢复 Markdown 和上述 delivery metadata，调用图片端口，成功后完成 Run 并消费 checkpoint；失败释放 claim，保留可恢复状态。
4. 新增 controller，只做 runId 参数校验和服务调用；不改旧 `ArticleController` 的 create/confirm 接口。
5. SSE 仅在图片成功后发送既有完成事件；E6.4A 不将 quality checkpoint 内容推给客户端。

## 最终 Artifact 版本规则

- Quality loop 已持久化的文章版本不可覆盖，也不能向其 Manifest 原地追加图片。
- 图片交付成功后，以质量通过版本为父版本创建一个新的 append-only delivery version；其章节草稿快照是最终合成 Markdown 的规范渲染快照，Manifest 同时登记最终 Markdown 和每个真实图片 URL 的 `IMAGE` Artifact（URL 的 SHA-256 仅用于地址完整性，不代表下载的二进制哈希）。
- 版本和 Manifest 均成功持久化后，Run 才允许转为 `COMPLETED` 并发送完成 SSE；任何一项失败都释放 checkpoint claim，保留 `WAITING_FOR_APPROVAL` 的可恢复边界。

## 当前实现说明

- `ContentQualityContinuationService` 通过 `WorkflowRecoveryService.resume` 领取 checkpoint；图片结果置于 `AgentNodeExecutionService` 的 `accepted-content-image-delivery` 持久化幂等节点。成功重试会反序列化已提交结果，而不是再次调用图片端口。
- 新接口为 `POST /api/agent-runs/{runId}/content-quality/continue`。在调用服务前复用 `ArticleService.getArticleDetail` 完成既有登录用户的文章归属检查；旧 `/article` 接口没有改动。
- 2026-08-30 集成验收复核发现：continue service 曾向图片端口传入空 handler，导致图片完成后的旧 SSE 事件没有实际送达客户端。该缺口必须在 E6 收口前修复：service 应经 `SseEmitterManager` 发送图片图产生的 legacy 消息，并仅在全部成功后完成 emitter。

## 验收状态（2026-08-30）

- 默认 Maven 回归已实际通过：74 项，0 失败、0 错误、0 跳过。
- Testcontainers 目前未执行：Docker Desktop Linux engine 未运行，持久化集成套件的 13 项均被跳过。此项保留为 E6 关闭前的阻塞验收，不得将跳过结果记为通过。

## 验收

- 单测：图片端口从 P3 Markdown 开始，永不调用 ContentGenerator；服务不执行 P3 adapter，也不读取旧文章记录作为恢复输入。
- 单测：错误/取消不完成 run；成功消费 checkpoint。
- Testcontainers：两个 continue 请求只有一个执行图片；图片副作用后失败重试复用；旧 flag 关闭路径无此 checkpoint。
- Web MVC：路径、空 body、成功响应与异常映射兼容。

## 回滚

关闭 P3 flag 阻止新 checkpoint；已有 P3 checkpoint 可显式取消。接口保留但不切换旧 run 到 P3。
