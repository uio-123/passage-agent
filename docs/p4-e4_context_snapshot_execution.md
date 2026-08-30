# P4 E4：Context Snapshot 与安全摘要

状态：已完成  
依赖：P1 checkpoint、P2 研究来源、P3 版本/质量结果、P4 E1 安全事件策略。  
范围：记录可恢复的阶段性上下文摘要及压缩 Token 统计；不实现向量记忆、不调用额外模型做摘要、不向 UI 暴露完整 Prompt、checkpoint 或网页内容。

## 设计

1. 新增 append-only `agent_context_snapshot`：`runId/sequence/stage/summary/tokenBefore/tokenAfter/createTime`。`(runId, sequence)` 唯一，序号独立于 Agent Event。
2. 上下文写入端只接受结构化、调用方提供的安全摘要；校验空值、阶段名和 Token 非负，摘要经统一清洗与长度限制。禁止从完整 `stateSnapshot`、模型消息或网页正文直接提取后落库。
3. 第一期接入 checkpoint 发布：仅记录节点、状态版本、checkpoint 标识和目标状态，Token 统计为未知时按 `0/0` 写入。P2/P3 后续接入必须独立记录其可计量 Token 来源，不能估造数值。
4. 提供经文章授权保护的只读 API，前端 E4 面板显示阶段、摘要、压缩前后 Token。无记录时明确显示未采集。

## 测试与回滚

- 单元：敏感字段与超长摘要清洗、Token 合法性、顺序读取。
- MVC：授权和响应不含 checkpoint stateSnapshot、Prompt/模型正文。
- Testcontainers：同 run 并发 snapshot 序号无重复、checkpoint 提交后追加摘要。
- 新配置 `article.agent.observability.context-snapshots-enabled=false` 默认关闭；关闭后不删历史记录且不影响 checkpoint。

## 实施结果

- 已新增 Context Snapshot 的 append-only 表、单 run 序号、脱敏/长度限制服务、授权读取 API 及文章详情快照面板。
- checkpoint 成功提交后可通过默认关闭的旁路发布器登记安全阶段摘要；事件或摘要失败均不影响 checkpoint 状态。
- 后端编译和前端生产构建已通过。
