# P3 E6.4：图片与最终交付执行清单

> 状态：`DONE`；图片图、恢复输入、continue、最终版本与接口均已接入，质量开关默认关闭
> 前置：E6.2 P3 正文分支与 E6.3 accepted-content checkpoint。

## 目标

仅在已恢复或刚产生的 `CONTENT_QUALITY_ACCEPTED` Markdown 上执行既有图片分析、并行图片和合成；最终才产生旧 `ARTICLE_COMPLETED` 与既有完成 SSE。P3 拒绝/失败绝不调用图片 Tool。

## 实施步骤

1. 已确认旧 phase3 是 `content_generator → image_analyzer → parallel_image_generator → content_merger` 单图，无法从 Runner 跳过首节点。先在 `ArticleAgentOrchestrator` 建立第二张只含 `image_analyzer → parallel_image_generator → content_merger` 的已接受正文图，并经独立 `ApprovedContentImageExecutor` 暴露；禁止再次调用 `ContentGeneratorAgent`。
2. 输入为 runId、已接受 Markdown、标题/风格/图片开关；输出为完整 Markdown、真实图片结果和 Artifact ID。旧占位符格式只在该适配器内部兼容。
3. `ArticleWorkflowRunner` 在 E6.3 成功 checkpoint 之后调用该适配器，并仅在成功后转 `COMPLETED`/发送旧正文和图片完成 SSE。
4. 将真实图片结果追加到最终 Manifest；不得覆盖 E5 的版本条目，若需要最终图片 Manifest 则以新 append-only 版本登记。

## 验收

- 默认：P3 flag 关闭仍运行旧 phase3；开启且 ACCEPT 时不调用 ContentGenerator、图片只运行一次；拒绝/失败时图片调用为零。
- Testcontainers：图片 Tool 成功但 checkpoint 提交失败后恢复不重复调用；最终版本包含真实图片 Artifact。
- SSE：旧 `AGENT3/4/5/MERGE_COMPLETE` 只在图片交付完成后发送；P3 中间阶段不得冒充这些完成事件。

## 回滚

关闭 P3 flag 走旧 phase3。已产生的版本、图片和 Artifact 保留审计；禁止删除或以旧全文覆盖 P3 已交付版本。
