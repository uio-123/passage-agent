# 框架兼容性记录

## 2026-08-25 候选矩阵

| 组件 | 已验证版本 | 验证结论 |
|---|---:|---|
| JDK | Microsoft OpenJDK 21.0.12.1 | Maven Enforcer 与 `mvn --offline test` 通过 |
| Spring Boot | 3.5.9 | 后端 Compose 健康检查通过 |
| Spring AI Alibaba Agent Framework | 1.1.2.2 | 最小 StateGraph 串行、并行汇合、事件流契约通过 |
| Spring AI OpenAI | 1.1.2 | OpenAI 客户端装配、LiteLLM 普通及流式冒烟通过 |
| LiteLLM | OpenAI-compatible endpoint | `/v1/models`、普通聊天、SSE 流式聊天通过 |

## 依赖解析证据

在 JDK 21 下执行：

```powershell
mvn.cmd '--define=maven.repo.local=target/m2' --offline dependency:tree '--define=includes=com.alibaba.cloud.ai:*,org.springframework.ai:*'
```

结果显示 `spring-ai-alibaba-agent-framework` 和 `spring-ai-alibaba-graph-core` 均为 `1.1.2.2`；`spring-ai-starter-model-openai`、`spring-ai-openai`、`spring-ai-model`、`spring-ai-client-chat` 及图框架传递的 Spring AI 模块均为 `1.1.2`。未发现解析回 `1.1.0` 的模块。

## 当前决策

**暂不冻结。** 候选矩阵已通过基础构建、OpenAI 兼容、StateGraph 串行/并行/流式 API 验证和真实 LiteLLM 冒烟，但项目尚未实现或配置持久化 checkpoint、interrupt/resume、Supervisor/ReactAgent Skill Registry、Policy Gateway 下的异步 Tool。因此这些能力不能以“未失败”替代验证。

继续 P0-U 前，必须先为上述项目自有边界增加 Fixture/Fake 契约；只有全部通过，才能将 `1.1.2.2 + 1.1.2` 标记为冻结基线并进入 P0-B。若出现核心不兼容，回退 `pom.xml` 中的两项版本属性至 `1.1.0.0-RC2` 和 `1.1.0`，保留 LiteLLM 迁移。
