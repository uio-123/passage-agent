# P4 E6：端到端验收与文档收口

状态：已完成  
依赖：P4 E1–E5。  
范围：验证 P4 的事件、详情、Artifact、Context 和治理能力，并同步对外文档；不新增功能或更改 P1–P3 业务状态机。

## 验收矩阵

| 需求 | 证据 |
|---|---|
| 统一事件与安全性 | 单元/默认回归；E1 API 实现使用安全 DTO 和 Last-Event-ID |
| 运行详情/Artifact/Context | 后端编译、前端生产构建、代码审计确认没有 snapshot/prompt/原文泄漏 |
| 管理治理 | 管理员注解、聚合只读取安全字段、无成本伪造 |
| 回归 | `mvn -q test`；`frontend npm run build` |
| 文档 | P4 总计划、E1–E6、README 和 development log 与实际功能一致 |

## 演示步骤

1. 部署所有 P4 SQL 迁移，设置 `events-enabled=true` 和 `context-snapshots-enabled=true` 后创建或继续一个有 Agent Run 的文章。
2. 打开文章详情：确认运行轨迹、版本/Manifest、Context 摘要均只显示安全信息；刷新后事件不重复。
3. 以管理员进入数据分析页，确认 P50/P95、失败率和告警候选出现；Token/成本仍显示未采集。
4. 关闭两个 feature flag，确认业务生成、旧文章 SSE、checkpoint 不受影响。

## 回滚

关闭两个 observability flag、隐藏新增只读面板/API 即可；不删除 append-only 事件、Context 或 Artifact 记录。若默认回归失败，修复后重新执行，不以文档状态代替测试。

## 当前验证结果

- `mvn -q test` 通过，共 75 项、0 失败、0 错误、0 跳过。首次重编译遇到项目已知的 Windows 编译器资源关闭问题，未改代码重跑后通过。
- `frontend npm run build` 通过。构建提示现有大包超过 500 kB，仅为优化提示，不阻断产物。
- 验收中发现 P1 已有 `ContextSnapshot` 领域契约。E4 已改用独立 `ObservabilityContextSnapshot`，恢复原有 P1 契约后回归通过。
- 新增专项单元测试覆盖 E1 Event payload 脱敏、E4 Context 摘要脱敏和长度限制、E5 分位数/失败率/未采集指标口径；三项专项测试通过。
