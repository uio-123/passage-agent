# 框架兼容性记录

## 2026-08-25 候选矩阵

| 组件 | 已验证版本 | 验证结论 |
|---|---:|---|
| JDK | Microsoft OpenJDK 21.0.12.1 | Maven Enforcer 与 `mvn --offline test` 通过 |
| Spring Boot | 3.5.9 | 后端 Compose 健康检查通过 |
| Spring AI Alibaba Agent Framework | 1.1.2.2 | 最小 StateGraph 串行、并行汇合、事件流契约通过 |
| Spring AI OpenAI | 1.1.2 | OpenAI 客户端装配、LiteLLM 普通及流式冒烟、本地 OpenAI Mock 的 JSON/SSE/错误协议验证通过 |
| LiteLLM | OpenAI-compatible endpoint | `/v1/models`、普通聊天、SSE 流式聊天通过 |

## 依赖解析证据

在 JDK 21 下执行：

```powershell
mvn.cmd '--define=maven.repo.local=target/m2' --offline dependency:tree '--define=includes=com.alibaba.cloud.ai:*,org.springframework.ai:*'
```

结果显示 `spring-ai-alibaba-agent-framework` 和 `spring-ai-alibaba-graph-core` 均为 `1.1.2.2`；`spring-ai-starter-model-openai`、`spring-ai-openai`、`spring-ai-model`、`spring-ai-client-chat` 及图框架传递的 Spring AI 模块均为 `1.1.2`。未发现解析回 `1.1.0` 的模块。

## 当前决策（2026-08-27）

**冻结 `Spring AI Alibaba 1.1.2.2 + Spring AI 1.1.2` 作为 P0-B 开发基线。** 除上述基础验证外，已增加三阶段业务链路 Fake 回归、图片部分失败后的稳定归并契约，以及 `MemorySaver` 的 interrupt/checkpoint/resume 隔离契约。恢复必须复用暂停快照携带的 `RunnableConfig`，而非仅凭初始 `threadId`，否则不会恢复已保存状态。

该结论不宣称完整业务恢复已完成：持久化 checkpoint、幂等外部副作用、取消传播和并发恢复一致性属于 P1；Skill Registry、Policy Gateway、异步 Tool 的超时/重试/预算属于 P2。若后续发现核心 API 不兼容，回退 `pom.xml` 中两项版本属性至 `1.1.0.0-RC2` 和 `1.1.0`，同时保留 LiteLLM 迁移与兼容性证据。
