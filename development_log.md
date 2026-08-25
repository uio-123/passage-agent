# 开发记录

## 2026-08-24  P0-A + P0-U：Java / LiteLLM 基线与框架升级验证

- 将模型客户端从 DashScope Starter 迁移为 Spring AI OpenAI Starter，经 LiteLLM 的 OpenAI 兼容接口访问模型；运行时变量统一为 `LITELLM_BASE_URL`、`LITELLM_API_KEY`、`LITELLM_MODEL`。
- 同步更新本地配置模板、生产配置、Docker Compose、启动脚本和 README。Compose 为 Linux Docker Engine 添加 `host.docker.internal:host-gateway` 映射，同时保持 Docker Desktop 默认可用。
- Maven 加入 Java 21 Enforcer 门禁，避免 JDK 17 在编译阶段才产生难以定位的错误。
- 候选依赖矩阵升级为 `spring-ai-alibaba-agent-framework:1.1.2.2` 与 `spring-ai-starter-model-openai:1.1.2`，与业务图改造保持分离。
- 新增无真实模型调用的 Spring Context 装配测试，以及默认排除的 `litellm-smoke` Maven Profile。后者只在三个 `LITELLM_*` 变量完整时验证 `/v1/models`、普通聊天和流式聊天接口。
- 已验证 IDEA 下载的 Microsoft OpenJDK `21.0.12.1` 可被 Maven 使用；Java 21 Enforcer 通过，候选依赖已解析，并通过不访问外部服务的 OpenAI 客户端装配测试。`docker compose config --quiet` 也已通过。
- Windows 环境在测试源码变更后的首次 Maven 编译偶发报告“无法关闭编译器资源”，但 class 文件已生成；无源码变更的离线复跑稳定通过。后续若再次出现，应先检查 IDE/杀毒软件是否占用 `target/test-classes`，再重跑；这不构成框架升级通过结论。
- 尚未执行 Docker 健康检查、真实 LiteLLM 普通/流式冒烟，以及 P0-U 的图、Tool、checkpoint 契约验证，因此暂不冻结版本矩阵。

## 2026-08-25  P0 真实环境验证跟进

- 使用 Microsoft OpenJDK `21.0.12.1` 临时设置 `JAVA_HOME` 后，`mvn --offline test` 通过：Java 21 Enforcer 与 LiteLLM OpenAI 兼容客户端装配测试均正常。
- 已以 `.env` 中的 LiteLLM 配置显式运行 `mvn -Plitellm-smoke test`。沙箱内连接被拒绝；获授权重试后连接仍在 10 秒内超时，说明当前代理地址不可达，尚未执行到模型列表、普通调用或流式调用。
- `docker compose ps` 显示 Docker daemon 未启动，故未执行 Docker 构建、健康检查及接口级验证。
- 后续前置条件：启动 LiteLLM 并确认 `LITELLM_BASE_URL` 从宿主机可访问；启动 Docker Desktop。之后重跑 LiteLLM 冒烟与 Docker 验证，再决定 P0-A 是否收口。

## 2026-08-25  P0 真实环境验证完成

- LiteLLM 容器恢复后，以宿主机地址 `http://localhost:4000` 运行 `mvn -Plitellm-smoke test` 成功。测试依次验证模型列表、一次普通聊天和一次 SSE 流式聊天；未记录密钥、提示词或生成正文。
- 使用 `maven:3.9-eclipse-temurin-21-alpine` 构建后端 Docker 镜像成功；镜像内 Maven 的 Java 21 Enforcer 已通过，编译目标为 `release 21`。
- 已执行 `docker compose up -d --build backend` 重建后端容器。后端与前端容器均已通过 Compose 健康检查；MySQL、Redis 继续保持 healthy。
- P0-A 的配置、构建、代理和容器健康基线已收口。尚未在已认证业务会话中补测“标题生成—流式正文”接口链路；P0-U 仍缺图、Tool、checkpoint 契约、依赖收敛记录和升级冻结结论，不能据此进入 P0-B。

## 2026-08-25  P0-U 候选依赖最小图验证

- 新增 `GraphCompatibilityTest`，在不访问模型、数据库或图片服务的条件下验证 `StateGraph 1.1.2.2` 的串行异步节点状态传递、并行分支汇合，以及 `stream` 节点事件输出。3 项图契约通过。
- JDK 21 下 `mvn --offline test` 通过，共 4 项默认测试；LiteLLM 冒烟仍由显式 profile 独立运行。
- 已执行依赖树检查：Alibaba Agent Framework 与 Graph Core 解析为 `1.1.2.2`；OpenAI Starter、Spring AI Model/OpenAI/Client Chat 及相关传递模块解析为 `1.1.2`，无 `1.1.0` 回落。详细矩阵见 `framework-compatibility.md`。
- 版本暂不冻结：项目尚无持久化 checkpoint / interrupt-resume、Supervisor / ReactAgent Skill Registry、Policy Gateway 异步 Tool 的实现边界，不能对这些未落地能力作兼容性结论。

### 回滚

若 `1.1.2.2 + 1.1.2` 在 Java 21 验证中出现核心兼容性问题，回退 `pom.xml` 中两个版本属性至 `1.1.0.0-RC2` 与 `1.1.0`，并保留 LiteLLM 配置迁移；不得将框架兼容补丁混入后续 P0-B 业务图改造。
