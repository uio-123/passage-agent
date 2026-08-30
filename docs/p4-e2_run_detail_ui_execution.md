# P4 E2：运行详情与实时 DAG UI

状态：已完成  
依赖：P4 E1 事件回放 API；现有文章详情页、`AgentRun` / `agent_node_execution` / checkpoint 读取边界。  
范围：在现有文章详情中增加按 run 查询的只读可观测面板；不新增管理端聚合、不采集新指标、不更改创作 API 或旧 SSE。

## 设计

1. 前端以 E1 的 snapshot + event replay 建立本地运行视图；连接 SSE 后以 sequence 去重，只接受严格更大的事件，断线时从最后 sequence 补拉。
2. 页面展示固定、安全的工作流 DAG（Run → checkpoint → 节点/质量/交付），父子 Run 关系、当前状态和事件时间线。未知/尚未接入的节点显示为“未记录”，不能伪造执行信息。
3. 仅展示 E1 payload 白名单字段、受控错误码和状态；不渲染任意 HTML、模型正文、Prompt 或状态快照。
4. 后端补充一个安全 read-model：按当前用户文章权限读取 root/child run、checkpoint 和 node execution 的状态投影。前端不直接推断数据库结构。

## API 与前端契约

| 接口/模块 | 契约 |
|---|---|
| `GET /api/agent-runs/{runId}/detail` | 授权后的只读 Run、子 Run、checkpoint、节点执行状态；不返回 snapshot/结果正文/错误原文 |
| `frontend/src/api/agentRun.ts` | 负责详情、事件回放、EventSource 生命周期与 sequence 去重 |
| `AgentRunObservabilityPanel.vue` | 接收 runId，展示 DAG、父子链路、时间线；卸载时关闭流 |

## 测试与回滚

- MVC：无权访问拒绝；详情不返回敏感 stateSnapshot/resultSnapshot/errorMessage；空事件也可显示持久化快照。
- 前端：sequence 去重、乱序忽略、断线补拉和组件卸载关闭 EventSource。
- 人工：在 feature flag 打开且有历史事件的 run 上刷新页面，状态与事件无重复。
- 回滚仅移除详情面板入口；E1 事件和旧文章详情不受影响。E2 不改数据库迁移。

## 实施结果

- 新增受文章授权保护的 `/api/agent-runs/{runId}/detail` 安全投影，只返回 Run 父子关系、checkpoint 和节点的标识、版本及状态；不返回 state/result snapshot 或错误正文。
- 文章详情新增 `AgentRunObservabilityPanel`，先加载安全快照与历史事件，再订阅 E1 事件流并按 sequence 去重；组件卸载即关闭连接。
- 验证：后端 `mvn -q -DskipTests compile` 通过（首次命中既有 Windows 编译器资源关闭问题，重跑通过）；前端 `npm run build` 通过。
