# Passage Agent ✍️

<div align="center">

**Passage Agent**

基于混合式 Agent Workflow，自动完成从选题、大纲、正文到配图的全流程图文创作

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Spring AI Alibaba](https://img.shields.io/badge/Spring%20AI%20Alibaba-1.1.2.2-FF6A00?style=flat-square&logo=spring&logoColor=white)
![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D?style=flat-square&logo=vuedotjs&logoColor=white)
![JDK](https://img.shields.io/badge/JDK-21+-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

</div>

## 🏗 项目简介

Passage Agent 是一个基于 **Spring AI Alibaba** 构建的智能图文创作平台。它以 Workflow 管理阶段状态、用户审批、恢复和副作用一致性，并在需要开放式推理的节点使用 Agent 生成内容、分析配图和处理后续研究/评审任务。

当前已上线的主链路是受控的标题—大纲—正文—配图工作流；Research、章节并行写作、评审与局部返工属于后续受限 Agent 能力，详见[开发计划](docs/plan.md)。

```
阶段1: 选题 → 生成 3-5 个标题方案 → 用户选择
阶段2: 标题 → 生成大纲 → 用户编辑 / AI 优化大纲
阶段3: 大纲 → 生成正文 → 分析配图需求 → 生成配图 → 图文合成
```

## 🎯 核心价值

| 特性 | 说明 | 价值 |
|------|------|------|
| 🤖 混合式 Agent Workflow | Workflow 管理状态、审批、恢复与幂等；Agent 处理生成与判断 | 既可控、可恢复，也能处理开放式创作任务 |
| 🎨 多元配图 | 6 种配图策略 + 自动降级 | 图文并茂，永不中断 |
| 📡 实时流式输出 | SSE 推送大纲/正文创作过程 | 所见即所得 |
| 🧑‍💻 人机协作 | 三阶段创作，每步可介入 | 创作可控 |
| 💎 VIP 会员体系 | Stripe 支付 + 配额管理 | 商业化就绪 |
| 🐳 Docker 一键部署 | docker compose up 即可运行 | 5 分钟上手 |

## ✨ 功能特性

### 当前创作工作流

| 智能体 | 功能 | 说明 |
|--------|------|------|
| Agent 1 | 标题生成 | 根据选题生成 3-5 个标题方案供用户选择 |
| Agent 2 | 大纲生成 | 根据标题生成文章大纲（流式输出） |
| Agent 3 | 正文生成 | 根据大纲生成 Markdown 正文（流式输出） |
| Agent 4 | 配图分析 | 分析正文内容，生成配图需求 |
| Agent 5 | 配图生成 | 获取图片并上传到 COS |
| 合成 | 合并图文 | 将配图插入正文生成完整图文 |

### 配图方式（策略模式）

系统采用策略模式实现多种配图方式，支持灵活扩展：

| 方式 | 说明 | 数据来源 | 权限 |
|------|------|---------|------|
| Pexels | 高质量图库检索 | 关键词检索 | 全部用户 |
| Mermaid | 流程图/架构图生成 | AI Prompt 生成 | 全部用户 |
| Iconify | 图标库检索 | 关键词检索 | 全部用户 |
| 表情包 | Bing 图片搜索 | 关键词检索 | 全部用户 |
| Nano Banana | Gemini AI 生图 | AI Prompt 生成 | VIP |
| SVG Diagram | AI 概念示意图 | AI Prompt 生成 | VIP |
| Picsum | 随机图片 | 降级方案 | 自动触发 |

> 当主配图方式失败时，系统会自动降级到 Picsum 随机图片，确保文章生成不中断。

### 文章风格

- 🔬 科技风格 - 专业严谨
- 💝 情感风格 - 温暖感人  
- 📚 教育风格 - 通俗易懂
- 😄 轻松幽默 - 诙谐有趣

### SSE 实时通信

基于 Server-Sent Events 实现实时进度推送：

| 消息类型 | 说明 |
|---------|------|
| `AGENT1_COMPLETE` | 标题方案生成完成 |
| `AGENT2_STREAMING` | 大纲流式输出中 |
| `AGENT2_COMPLETE` | 大纲生成完成 |
| `AGENT3_STREAMING` | 正文流式输出中 |
| `AGENT3_COMPLETE` | 正文生成完成 |
| `AGENT4_COMPLETE` | 配图需求分析完成 |
| `IMAGE_COMPLETE` | 单张配图生成完成 |
| `AGENT5_COMPLETE` | 所有配图生成完成 |
| `MERGE_COMPLETE` | 图文合成完成 |
| `ERROR` | 错误通知 |

### 其他特性

- ✅ 文章管理（列表、详情、删除）
- ✅ Markdown 导出
- ✅ VIP 会员体系（Stripe 支付）
- ✅ 智能体执行日志追踪（AOP 自动记录）
- ✅ 管理后台统计分析

## 🛠 技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.5.9 | Web 框架 |
| Spring AI Alibaba Agent Framework | 1.1.2.2 | 多智能体编排框架 |
| Spring AI OpenAI Client | 1.1.2 | 通过 LiteLLM 调用 OpenAI 兼容模型 |
| MyBatis-Flex | 1.11.1 | ORM 框架 |
| MySQL | 8.0 | 数据存储 |
| Spring Data Redis | - | Redis 客户端 |
| Redisson | 3.50.0 | 分布式锁 |
| Stripe | 31.2.0 | 支付集成 |
| Knife4j | 4.4.0 | 接口文档 |
| 腾讯云 COS SDK | 5.6.228 | 对象存储 |
| Google Gen AI SDK | 1.35.0 | Gemini AI 生图 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5 | 前端框架 |
| TypeScript | 5.8 | 类型安全 |
| Ant Design Vue | 4.2 | UI 组件库 |
| Vite | 7.0 | 构建工具 |
| Pinia | 3.0 | 状态管理 |
| Vue Router | 4.5 | 路由管理 |
| ECharts | 6.0 | 数据可视化 |
| Axios | 1.11 | HTTP 客户端 |

## 🚀 快速开始

### 环境要求

- JDK 21（构建会拒绝 JDK 17 或其他版本）
- Node.js 18+
- MySQL 8.0+
- Redis 7.x

### 1. 数据库初始化

```bash
mysql -uroot -p < sql/create_table.sql
# Existing databases also need the Agent run persistence migration:
mysql -uroot -p < sql/add_agent_run_tables.sql
# Then apply the resumable-workflow checkpoint migration:
mysql -uroot -p < sql/add_agent_workflow_persistence.sql
```

### 2. 配置 API Key

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

编辑 `application-local.yml`：

```yaml
spring:
  ai:
    openai:
      base-url: ${LITELLM_BASE_URL:http://localhost:4000}
      api-key: ${LITELLM_API_KEY:your-litellm-api-key}
      chat:
        options:
          model: ${LITELLM_MODEL:your-model-name}

pexels:
  api-key: your-pexels-api-key  # 必填

# 可选配置
stripe:
  api-key: sk_test_xxx  # 支付功能
  
tencent:
  cos:
    secret-id: xxx  # 图片上传
```

### 3. 启动后端

```bash
mvn spring-boot:run
```

本地运行 LiteLLM 后，先在环境中设置 `LITELLM_BASE_URL`、`LITELLM_API_KEY` 和
`LITELLM_MODEL`。例如 PowerShell：

```powershell
$env:LITELLM_BASE_URL = "http://localhost:4000"
$env:LITELLM_API_KEY = "your-litellm-api-key"
$env:LITELLM_MODEL = "your-model-name"
```

接口文档：http://localhost:8567/api/doc.html

离线测试不会访问 LiteLLM。配置完三项 `LITELLM_*` 变量后，可显式运行代理冒烟测试（仅验证模型列表、普通调用和流式调用的状态，不输出密钥或正文）：

```bash
mvn test -Plitellm-smoke
```

Docker 可用时，可执行 P1 持久化集成测试；该 Profile 会创建并自动清理临时 MySQL 容器，不访问 LiteLLM、COS 或 Pexels：

```bash
mvn test -Ppersistence-integration
```

P1 已提供 Supervisor 受限路由、持久化 checkpoint、取消优先恢复和图片副作用幂等：同一图片节点在 checkpoint 尚未推进时重试，会复用已保存的首次结果，不会再次调用图片服务。真实研究 Tool、统一超时/预算/审计仍属于后续 P2。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端页面：http://localhost:5173

## 🐳 Docker 一键部署（推荐）

### 前置条件

- Docker 20.10+
- Docker Compose v2+

### 快速启动

```bash
# 1. 复制环境变量配置文件
cp .env.example .env

# 2. 编辑 .env 文件，填写必需的 API Key
# 必须配置：LITELLM_BASE_URL、LITELLM_API_KEY、LITELLM_MODEL 和 PEXELS_API_KEY
vim .env

# 3. 一键启动所有服务
docker compose up -d --build

# 或使用启动脚本（自动检查环境）
./start.sh
```

### 国内网络使用（镜像加速）

如果遇到 Docker 镜像拉取失败，使用国内镜像版本：

```bash
docker compose -f docker-compose.china.yml up -d --build
```

### LiteLLM 网络说明

- Windows/macOS Docker Desktop：默认 `LITELLM_BASE_URL=http://host.docker.internal:4000`，容器可访问宿主机上的 LiteLLM。
- Linux Docker Engine：Compose 已通过 `host-gateway` 映射 `host.docker.internal`；如 LiteLLM 不在宿主机，请将 `LITELLM_BASE_URL` 改为其可访问的容器服务地址。
- 本机直接运行后端：使用 `http://localhost:4000`，或填写实际 LiteLLM 地址。

### 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 | 80 | 访问地址：http://localhost |
| 后端 | 8123 | API 接口：http://localhost:8123/api |
| 接口文档 | 8123 | http://localhost:8123/api/doc.html |
| MySQL | 不暴露 | 仅内部网络访问（可选暴露到 13306） |
| Redis | 不暴露 | 仅内部网络访问（可选暴露到 16379） |

> **安全说明**：MySQL 和 Redis 默认不暴露端口到宿主机，仅通过 Docker 内部网络访问。如需从宿主机连接数据库进行调试，可在 `docker-compose.yml` 中取消相应 `ports` 注释。

### 常用命令

```bash
# 查看服务状态
docker compose ps

# 查看服务日志
docker compose logs -f backend    # 后端日志
docker compose logs -f frontend   # 前端日志
docker compose logs -f mysql      # 数据库日志

# 重启单个服务
docker compose restart backend

# 停止所有服务
docker compose down

# 停止并删除数据卷（清空数据）
docker compose down -v
```

### 环境变量说明

| 变量名 | 必需 | 默认值 | 说明 |
|--------|------|--------|------|
| LITELLM_BASE_URL | ✅ | `http://host.docker.internal:4000` | LiteLLM OpenAI 兼容接口地址 |
| LITELLM_API_KEY | ✅ | - | LiteLLM 虚拟 Key / 代理要求的 Key |
| LITELLM_MODEL | ✅ | - | LiteLLM 路由的模型名 |
| PEXELS_API_KEY | ✅ | - | Pexels 图片 API Key |
| MYSQL_ROOT_PASSWORD | - | 123456 | MySQL root 密码 |
| MYSQL_DATABASE | - | ai_passage_creator | 数据库名 |
| BACKEND_PORT | - | 8123 | 后端端口 |
| FRONTEND_PORT | - | 80 | 前端端口 |
| NANO_BANANA_API_KEY | - | - | AI 生图（VIP功能） |
| STRIPE_API_KEY | - | - | Stripe 支付（VIP功能） |

详见 `.env.example` 文件获取完整配置说明。

## 📁 项目结构

```
├── src/main/java/com/passage/agent/
│   ├── agent/                       # 智能体模块
│   │   ├── agents/                  # 各智能体实现
│   │   │   ├── TitleGeneratorAgent.java
│   │   │   ├── OutlineGeneratorAgent.java
│   │   │   ├── ContentGeneratorAgent.java
│   │   │   ├── ImageAnalyzerAgent.java
│   │   │   └── ContentMergerAgent.java
│   │   ├── parallel/                # 并行配图生成
│   │   │   └── ParallelImageGenerator.java
│   │   ├── config/                  # 智能体配置
│   │   ├── context/                 # 流式处理上下文
│   │   ├── tools/                   # 智能体工具
│   │   └── ArticleAgentOrchestrator.java
│   ├── annotation/                  # 自定义注解（@AgentExecution）
│   ├── aop/                         # AOP 切面（执行日志记录）
│   ├── config/                      # 配置类（COS、Pexels、Mermaid 等）
│   ├── constant/                    # 常量（PromptConstant、ArticleConstant）
│   ├── controller/                  # 控制器
│   ├── exception/                   # 异常处理
│   ├── manager/                     # 管理器（SseEmitterManager）
│   ├── mapper/                      # MyBatis Mapper
│   ├── model/
│   │   ├── dto/                     # 数据传输对象
│   │   │   ├── article/             # ArticleState、ArticleCreateRequest 等
│   │   │   └── image/               # ImageData、ImageRequest
│   │   ├── entity/                  # 实体类
│   │   ├── enums/                   # 枚举（ImageMethodEnum、ArticleStyleEnum）
│   │   └── vo/                      # 视图对象
│   ├── service/                     # 业务服务
│   │   ├── impl/                    # 服务实现
│   │   ├── ArticleAgentService.java # 智能体编排
│   │   ├── ImageServiceStrategy.java# 配图策略选择器
│   │   ├── CosService.java          # COS 上传
│   │   ├── PexelsService.java       # Pexels 图库
│   │   ├── NanoBananaService.java   # Gemini AI 生图
│   │   ├── MermaidService.java      # Mermaid 流程图
│   │   ├── IconifyService.java      # Iconify 图标
│   │   ├── EmojiPackService.java    # 表情包搜索
│   │   └── SvgDiagramService.java   # SVG 示意图
│   └── utils/                       # 工具类
├── frontend/                        # 前端项目
│   ├── src/
│   │   ├── pages/                   # 页面组件
│   │   ├── components/              # 公共组件
│   │   ├── api/                     # API 接口
│   │   └── stores/                  # 状态管理
│   └── package.json
├── sql/                             # 数据库脚本
│   ├── create_table.sql             # 建表语句
│   ├── add_agent_run_tables.sql      # Agent 运行记录迁移
│   ├── init_database.sql            # 初始化数据
│   └── ...                          # 增量更新脚本
├── docker-compose.yml               # Docker 编排
├── start.sh                         # 启动脚本
└── pom.xml                          # Maven 配置
```

## 🗄 数据库设计

### 核心表

| 表名 | 说明 |
|------|------|
| user | 用户表（含 VIP 时间、配额） |
| article | 文章表（含状态、阶段、配图方式限制） |
| agent_log | 智能体执行日志 |
| payment_record | 支付记录 |

### 文章表关键字段

```sql
taskId               -- 任务ID（UUID）
phase                -- 当前阶段：TITLE_SELECTION/OUTLINE_EDITING/CONTENT_GENERATION/COMPLETED
style                -- 文章风格
titleOptions         -- 标题方案列表（JSON）
enabledImageMethods  -- 允许的配图方式（JSON 数组）
```

## 🔑 API Key 获取

| 服务 | 获取地址 | 说明 |
|------|---------|------|
| LiteLLM 代理及其配置的模型提供方 | 按部署环境配置 | 必需；使用 `LITELLM_BASE_URL`、`LITELLM_API_KEY`、`LITELLM_MODEL` |
| Pexels | https://www.pexels.com/api/ | 必需 |
| Stripe | https://dashboard.stripe.com | 支付功能 |
| 腾讯云 COS | https://console.cloud.tencent.com | 图片上传 |
| Nano Banana | - | Gemini AI 生图（VIP 功能） |

## 🧪 测试账号

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | 12345678 | 管理员 |
| user | 12345678 | 普通用户 |
| test | 12345678 | 测试账号 |

## 🏛 架构特点

### 混合式 Agent Workflow

Workflow 是控制平面：负责状态流转、Human-in-the-loop 审批、checkpoint 恢复、取消、并发和副作用幂等；Agent 是受约束的认知执行单元：负责标题、大纲、正文、配图分析，以及后续的研究、评审和局部返工。图片检索、上传和 Markdown 合成等确定性操作仍是 Tool/Service，不作为 Agent。

当前采用 Spring AI Alibaba 的 StateGraph 承载确定性创作工作流：

```java
StateGraph graph = new StateGraph(keyStrategyFactory)
    .addNode("content_generator", node_async(contentGeneratorAgent))
    .addNode("image_analyzer", node_async(imageAnalyzerAgent))
    .addNode("parallel_image_generator", node_async(parallelImageGenerator))
    .addNode("content_merger", node_async(contentMergerAgent))
    .addEdge(START, "content_generator")
    .addEdge("content_generator", "image_analyzer")
    .addEdge("image_analyzer", "parallel_image_generator")
    .addEdge("parallel_image_generator", "content_merger")
    .addEdge("content_merger", END);
```

### 配图策略模式

支持 6 种配图方式，通过策略模式实现灵活扩展：

```java
public enum ImageMethodEnum {
    PEXELS("PEXELS", "Pexels 图库", false, false),
    NANO_BANANA("NANO_BANANA", "AI 生图", true, false),
    MERMAID("MERMAID", "流程图", true, false),
    ICONIFY("ICONIFY", "图标库", false, false),
    EMOJI_PACK("EMOJI_PACK", "表情包", false, false),
    SVG_DIAGRAM("SVG_DIAGRAM", "示意图", true, false);
}
```

### 流式输出

基于 SSE（Server-Sent Events）实现实时进度推送：

- 大纲生成流式输出
- 正文创作流式输出
- 配图生成实时通知
- 阶段状态实时更新

## 🔧 扩展指南

### 添加新的配图方式

1. 在 `ImageMethodEnum` 添加枚举值：

```java
NEW_METHOD("NEW_METHOD", "新方式描述", isAiGenerated, isFallback)
```

2. 实现 `ImageSearchService` 接口：

```java
@Service("NEW_METHOD")
public class NewMethodService implements ImageSearchService {
    @Override
    public ImageData search(ImageRequest request) {
        // 实现图片获取逻辑
    }
}
```

3. 添加对应的配置类（如需要 API Key）
4. 策略选择器会自动注册新服务

### 添加新的文章风格

1. 在 `ArticleStyleEnum` 添加枚举值
2. 在 `PromptConstant` 添加对应的 Prompt 附加内容
3. 在 `ArticleAgentService.getStylePrompt()` 添加 case

## 📖 相关文档

- [文档索引](docs/README.md) - 开发计划、执行文档、兼容性记录与业务说明
- [P1.5 执行文档](docs/p1.5_execution_plan.md) - 项目身份迁移的范围、兼容性与验证结果
- [VIP 功能说明](docs/vip_features.md) - VIP 会员权益介绍
- [Stripe 支付配置](docs/stripe_setup.md) - 支付功能配置指南
