# P4 E1：统一 Agent Event、持久化回放与 SSE 重连

状态：已完成  
依赖：`p4_execution_plan.md`；现有 `AgentRun`、checkpoint、节点执行和旧文章 SSE。  
范围：建立可追加、可授权读取的统一运行事件流；不迁移或删除旧文章 SSE，不实现 UI、指标聚合或上下文压缩。

## 设计

1. 新增项目自有 `AgentEvent` DTO：`runId`、单 run 严格递增 `sequence`、事件类型、时间、可选 node/agent/attempt、受限 payload 摘要。
2. 事件类型首批覆盖 Run/节点/Tool/评审/checkpoint/终态；使用枚举，未知类型和不安全 payload 在发布边界拒绝。
3. 新建 append-only `agent_event` 表，以 `(run_id, sequence)` 唯一约束作为顺序与幂等边界；数据库为 sequence 分配提供并发安全实现，不依赖 JVM 锁。
4. 提供旁路 `AgentEventPublisher`。业务节点在关键状态提交后发布事件；发布失败记录受控日志且不得影响原业务结果。第一批只接入已有 run 创建、checkpoint、恢复、节点执行和 P3 continuation 边界。
5. 提供已授权的只读 API：快照 + `afterSequence` 增量查询，以及 `text/event-stream`。SSE 读取 `Last-Event-ID`，先发送状态快照，再按 sequence 补发缺失事件；服务端事件 id 等于 sequence。
6. payload 使用固定白名单字段和长度限制；禁止全量 state snapshot、Prompt、模型输出正文、HTTP header、密钥或网页正文。

## API 契约

| 接口 | 行为 |
|---|---|
| `GET /api/agent-runs/{runId}/events?afterSequence=N` | 返回当前安全 run 快照及 sequence 大于 N 的有限事件页 |
| `GET /api/agent-runs/{runId}/events/stream` | 读取 `Last-Event-ID`，先发 `snapshot`，后发递增 `agent-event`，连接保持期间订阅新事件 |

读取必须验证当前登录用户对同一 article/run 的访问权；找不到 run、无权访问或非法 sequence 均返回现有错误模型。SSE 断开不修改 run 状态。

## 数据迁移与兼容性

新增 `sql/add_agent_event_persistence.sql` 并挂载到 Compose MySQL 初始化。表仅新增，不修改既有 run/checkpoint/Artifact 表。配置 `article.agent.observability.events-enabled` 默认 `false`，开启后才发布新事件；旧 SSE 行为不变。

## 测试矩阵

- 纯单元：事件 DTO 校验、payload 脱敏、单 run sequence、publisher best-effort。
- MVC：授权、`afterSequence`、`Last-Event-ID`、SSE 事件 id 与 snapshot 顺序。
- Testcontainers：并发追加无重复 sequence、顺序读取、运行/checkpoint 状态提交后才有对应事件。
- 回归：默认 `mvn test` 与 persistence-integration；旧文章 SSE 测试不变。

## 回滚与非目标

关闭 flag 后不再产生新事件且不删除历史记录；读 API 可保留为只读。E1 不改前端、不采集 Token/成本、不改变 Framework hooks、不提供跨 run 查询或管理端统计。

## 实施结果

- 已新增 `agent_event_sequence` 与 append-only `agent_event` 迁移，并加入 Compose 初始化顺序；默认 `article.agent.observability.events-enabled=false`。
- 已实现项目自有安全事件 DTO、禁止敏感字段的 payload 清洗、严格事件序号、回放 API 与 `Last-Event-ID` SSE 补发；新 SSE 多订阅管理器不影响旧文章单 emitter。
- Run、checkpoint 与节点边界仅以 best-effort 方式发布；事件写入异常会被隔离，不能改变工作流结果。
- 验证：`mvn -q test` 通过。首次两次编译命中项目既有 Windows 编译器资源关闭问题，未改代码重跑后通过。
