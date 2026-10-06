# 五分钟 Demo 操作手册

> 目标：在面试或项目讲解中用最短路径展示最有价值的工程能力。

## 1. 准备

需要：

- JDK 21、Maven、Docker Desktop
- Node.js 18+
- 可用的 LiteLLM 地址和模型
- `.env` 或当前 shell 中正确设置：

```text
LITELLM_BASE_URL
LITELLM_API_KEY
LITELLM_MODEL
```

当前本机验证使用：

```text
LITELLM_BASE_URL=http://localhost:4000
LITELLM_API_KEY=local-test-key
LITELLM_MODEL=qwen3.7-flash
```

如果后端运行在 Docker 内，地址通常要改为：

```text
LITELLM_BASE_URL=http://host.docker.internal:4000
```

## 2. 快速工程验证

```bash
node scripts/ci/run-l0.mjs
mvn -q clean test
cd frontend && npm ci && npm run build
```

预期：

- L0 通过
- 默认后端 109 项通过
- 前端生产构建通过

## 3. 无 Key 确定性 Demo

适合先证明 UI、MySQL、Redis、Run、Event、ArticleVersion 和 Artifact 链路：

```bash
docker compose --env-file .env.demo.example -f docker-compose.yml -f docker-compose.demo.yml up -d --build
node scripts/demo/smoke.mjs http://localhost:18123 300000
```

浏览器打开：

```text
http://localhost:18080
```

展示内容：

1. 服务健康检查
2. 一次完整 Run
3. 事件回放
4. 版本链和 Artifact
5. 重复请求幂等

停止 Demo：

```bash
docker compose --env-file .env.demo.example -f docker-compose.yml -f docker-compose.demo.yml down
```

`down -v` 只在明确需要清空 Demo 数据时使用。

## 4. 真实模型 Demo

启动主栈：

```bash
docker compose up -d --build
```

前端：

```text
http://localhost
```

演示路径：

1. 创建一篇科技或教育主题文章。
2. 查看标题候选并选择一个标题。
3. 编辑或确认大纲。
4. 观察正文 SSE 流式输出和配图进度。
5. 打开文章详情，展示运行轨迹、Checkpoint、Artifact 和事件。

演示时不要声称：

- P3 质量闭环已作为默认链路开启。
- Harness 已经证明质量优于旧 Workflow。

## 5. H4 证据演示

准备：

```powershell
$env:H4_LITELLM_BASE_URL = "http://localhost:4000"
$env:H4_LITELLM_API_KEY = "local-test-key"
$env:H4_LITELLM_MODEL = "qwen3.7-flash"
```

执行：

```powershell
mvn -q -Pharness-staging -Dtest=HarnessStagingComparisonTest test
```

报告目录：

```text
harness/reports/<run-id>/report.json
```

重点展示：

- 旧 Workflow 与 Harness 的成功率
- Harness 的模型调用和 Token 增量
- Reviewer 返工轮数
- 恢复结果和重复副作用
- 默认开关保持关闭的决策

## 6. 推荐演示顺序

1. 用一句话介绍项目定位。
2. 展示架构图。
3. 跑确定性 Demo。
4. 跑真实模型主链路。
5. 展示 H4 报告和取舍。
6. 明确说明当前实验项与默认项的区别。

## 7. 常见故障

### 模型调用 502

检查：

- LiteLLM 容器是否健康
- `LITELLM_BASE_URL` 是否能从当前进程访问
- `LITELLM_MODEL` 是否是 LiteLLM 已注册且健康的模型

### Docker 容器健康但模型不可用

Docker 内的 `localhost` 指向容器自身，通常应使用：

```text
http://host.docker.internal:4000
```

### 前端构建通过但接口失败

检查前端 API 地址：

```text
VITE_API_BASE_URL
```

生产前端使用 `/api`，由 Nginx 反向代理到后端。
