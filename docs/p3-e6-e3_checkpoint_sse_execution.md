# P3 E6.3：Checkpoint、SSE 与恢复兼容执行清单

> 状态：`DONE`；accepted-content checkpoint、恢复适配与 SSE 兼容已验证
> 前置：E6.2 Runner flag 分支已完成；不在本步接入图片。

## 目标

为 P3 主流程保存可恢复的“正文质量已通过”边界，并保持旧 HTTP/SSE 客户端兼容。恢复必须沿用 P1 的 CAS checkpoint 领取机制，不能重复 Writer、Revision 或 Artifact。

## 快照契约

新增 JSON `P3ContentCheckpointSnapshot`，字段固定为：`runId`、`workflowMode=P3_QUALITY_LOOP`、`stateVersion`、`markdown`、`articleVersion`、`artifactIds`、`qualityDecision=ACCEPT`。不保存 Prompt、模型原始响应、网页正文、完整 Review、Token 或密钥。

仅在 Gate `ACCEPT` 且 P3 Artifact 已持久化后创建 checkpoint：

- `nodeId=content-quality-accepted`
- target Run 状态为 `WAITING_FOR_APPROVAL`
- checkpoint ID 为 `runId:content-quality-accepted:{expectedStateVersion}` 的稳定 UUID/名称 UUID，而非随机 UUID

“质量拒绝”或 Writer/Revision 失败不创建成功 checkpoint；仍由对应 node execution 的 `FAILED` 状态和后续 P1 失败/恢复路径处理。

## Runner/恢复行为

1. E6.2 的 P3 分支成功后序列化快照，并用实际持久化 `stateVersion` 调用 `persistCheckpoint`。
2. 返回 `CONTENT_QUALITY_ACCEPTED`，Run 为 `WAITING_FOR_APPROVAL`；此阶段不能调用图片或 `COMPLETED`。
3. 新增应用级恢复适配器，从 `WorkflowRecoveryService.resume` 获得 claimed checkpoint，解析且校验快照，然后回填 typed `WorkflowState` 的 content。该适配器不重跑 P3 模型/节点。
4. 只有状态和模式均匹配的 checkpoint 可走该适配器；旧 checkpoint 继续由原恢复路径消费，不能更改旧 HTTP 参数或响应结构。

## SSE 兼容

- 保持旧正文完成字符串消息仅在 E6.4 图片合成结束后发送，E6.3 不发送该旧完成事件。
- 可增加内部/可忽略事件 `p3_quality:accepted:{sectionCount}`，经现有 StreamHandlerContext 发出；不增加客户端必须解析的 JSON 协议。
- 不发送 Markdown 正文、章节问题、来源摘要或错误堆栈；HTTP 错误仍使用既有 `WorkflowExecutionException` 映射。

## 测试与回滚

| 层级 | 场景 | 断言 |
|---|---|---|
| 默认 | P3 ACCEPT | 写出含白名单字段的快照、返回质量通过阶段、未调图片 |
| 默认 | 拒绝/失败 | 不创建成功 checkpoint、不发送完成 SSE |
| 默认 | 恢复解析 | 仅回填 Markdown、不调 P3 adapter |
| Testcontainers | checkpoint 后进程中断 | CAS 只允许一个恢复者，恢复无重复 Writer/Revision/Artifact |
| Testcontainers | 取消与恢复竞态 | 取消优先，checkpoint 不可恢复 |

回滚为关闭 flag；已创建 checkpoint 与 P3 版本/Artifact 保留审计。若发现快照可泄露正文/来源数据、恢复可重跑副作用或旧 SSE 事件顺序变化，停止实施并保持 flag 关闭。
