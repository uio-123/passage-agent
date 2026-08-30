# P2 E2：Policy Gateway 与安全 Web Reader 执行计划

> 状态：已完成
> 日期：2026-08-29
> 依据：[p2_execution_plan.md](p2_execution_plan.md)、[plan.md](plan.md)、P2 E1 已完成的 Tool/Research 契约。

## 1. 目标与完成定义

E2 在 Search/Web Reader 的任何真实网络实现之前，提供唯一、默认拒绝的 `Policy Gateway`。所有 P2 研究 Tool 都必须经它完成授权、目标地址校验、重定向复检、调用预算、超时、有限重试和脱敏审计；本 E 不接入外部搜索供应商、不写数据库、不替换 Supervisor 的 research 占位。

完成时，项目应具备一个可以由 Fake HTTP/Tool 验证的安全读取边界：安全目标得到受限文本和元数据，不安全或超限目标被拒绝且产生脱敏结果。不存在可绕过 Gateway 的 P2 HTTP Tool 调用路径。

## 2. 范围与不可变约束

### 本 E 范围

- 定义 Tool Registry 与唯一 `PolicyGateway` 应用接口；Registry 初始仅登记 `WEB_READER` 的受控适配器，`SEARCH` 仍为未实现状态。
- 定义 URL/网络策略、请求时间预算、响应字节/文本长度上限、最大重定向次数、可重试错误分类和脱敏审计 DTO。
- 实现只读 Web Reader 的 HTTP 适配器和内容规范化：仅输出 canonical URL、标题（可空）、受限纯文本摘要/正文和内容哈希；不执行网页脚本或页面指令。
- 建立无公网的 MockWebServer/Fake DNS/HTTP 回归：授权、协议、回环/私网、重定向、超时、响应上限、恶意文本、重试与审计。

### 明确不在本 E 范围

- 真实 Search API、密钥配置、供应商 SDK、浏览器自动化和公网集成测试。
- `research_source` / `tool_call_audit` 表、Mapper、Testcontainers（属于 E3）。
- Research Agent、Skill Registry、Supervisor 主图接入（属于 E4/E5）。
- 修改图片 Tool 或将既有图片路径接入 Gateway；仅预留未来可迁移接口。

### 不可变约束

- 默认测试完全离线；HTTP 回归只访问本地 MockWebServer。
- Tool ID 必须同时通过 `ToolCallRequest.allowedTools` 和 Registry 的已注册检查；未知或未授权 Tool 默认拒绝。
- `WEB_READER` 仅允许 HTTPS；本地 Mock 场景通过仅测试可见的 transport seam 模拟，不放宽生产策略。
- 每个重定向目标都必须重新做协议、主机与解析地址校验；不得只校验初始 URL。
- Agent/调用方不能传入“跳过检查”的布尔开关；`returnDirect` 不提供给 Research/Web Reader。
- 审计不得保存 Authorization、Cookie、API Key、完整响应正文或未脱敏 URL 查询参数。

## 3. 设计方案

### 3.1 包与依赖边界

```text
agent/tool/
  ToolRegistry              # 仅注册项目拥有的 Tool Adapter
  ToolAdapter               # 接收已校验请求，返回 ToolCallResult
  ToolCallRequest/Result    # E1 已有项目契约
agent/policy/
  ToolPolicyGateway         # 唯一公开执行入口
  ToolPolicy                # 限额、超时、重试、响应大小、重定向配置
  UrlSafetyValidator        # 协议、host、IP/DNS 与重定向目标校验
  ToolCallAuditEvent        # 脱敏且暂存内存的审计结果
agent/tool/web/
  WebReaderToolAdapter      # HTTP 获取、大小限制、文本规范化；不含策略绕过
```

业务代码、Supervisor 与未来 Research Agent 只能依赖 `ToolPolicyGateway`。`WebReaderToolAdapter` 不能直接被 Spring Controller 或 Agent 注入；E3 再将审计持久化为 Service/Mapper。

### 3.2 网络策略

1. 解析 URL，拒绝非 `https`、无 host、userinfo、非默认端口（首版只允许 443）和超过长度限制的地址。
2. 对 host 执行注入式 DNS 解析器；每个解析结果均拒绝 IPv4/IPv6 的 loopback、private、link-local、site-local、multicast、unspecified 与云元数据地址。
3. HTTP 客户端禁用自动重定向；适配器收到 3xx 时经 Gateway 校验 `Location` 后才手动继续，最多三次。
4. 连接与读取使用独立、有限超时；读取流时严格限制响应字节数和规范化后的文本字符数，超限立即中止。
5. 仅对连接超时、临时 5xx 和 429 等分类允许有限重试；不对策略拒绝、4xx、响应超限或内容解析错误重试。

### 3.3 内容与审计策略

- 只接受 `text/html`、`text/plain` 与明确允许的文本 MIME；其他内容类型拒绝。
- HTML 仅提取标题和可见文本；脚本、样式、表单、注释和 HTML 指令不进入返回内容。模型侧仍应把网页文本视为不可信数据；E4 的 Research Prompt 需再次明确这一点。
- 审计记录 run ID、Tool ID、目标 URL 的 origin + 路径哈希、结果代码、错误分类、耗时、重试次数和响应大小；日志/异常中也不得回显敏感请求头或正文。

## 4. 实施步骤

1. 新增 Registry、Adapter、Gateway、Policy、审计 DTO 与错误枚举；先完成未知 Tool/未授权 Tool/预算耗尽的离线契约。
2. 实现 `UrlSafetyValidator` 与可替换 DNS/HTTP transport；覆盖 URL 格式、协议、端口和各类受限地址。
3. 实现手动重定向、超时、大小限制和有限重试；将所有出站调用收敛到 Adapter 的单一 transport seam。
4. 实现 HTML 文本规范化和内容哈希，保证 ToolResult 不含脚本/样式内容。
5. 用 MockWebServer 与 Fake DNS 覆盖安全矩阵；接入 Spring Bean 时只暴露 Gateway。
6. 更新 P2 文档、开发记录，并执行默认回归；不新增真实配置项或 Docker 服务。

## 5. 验收测试矩阵

| 场景 | 预期 |
|---|---|
| 未注册 / 未授权 Tool | Gateway 在网络请求前拒绝，并产生脱敏拒绝审计 |
| `http`、userinfo、非 443 端口 | 策略拒绝 |
| IPv4/IPv6 loopback、私网、链路本地、云元数据 | DNS/地址校验拒绝 |
| 初始地址安全、重定向到私网 | 第二跳前拒绝；不发出第二跳请求 |
| DNS 重绑定模拟 | 每次连接/重定向重新解析并拒绝新私网地址 |
| 429 / 临时 5xx | 仅在上限内重试，审计包含重试次数 |
| 4xx、策略拒绝、超大响应、非文本 MIME | 不重试，产生明确错误分类 |
| HTML 含恶意提示、脚本、样式 | 返回中不含脚本/样式；文本保留为不可信资料，不触发指令 |
| 成功响应 | 有 canonical URL、标题/文本、哈希、耗时和不含敏感信息的审计 |

## 6. 验证、回滚与风险

- 必跑：`git diff --check`、定向 Gateway/Web Reader 测试、默认 `mvn test`。若 Windows 编译器资源关闭问题出现，只能在无源码修改后重跑并记录结果。
- 不运行公网测试；真实供应商连接仅在 E4/E5 后以显式 profile 单独验证。
- 回滚：Gateway/Registry 只新增 P2 绑定且未接入主 Workflow；禁用 Spring 绑定或回退本 E 提交即可恢复 P1 research 占位，不影响图片生成和已持久化 Run。
- 主要风险：DNS 校验与实际连接之间存在 TOCTOU 窗口。首版通过禁用自动重定向、每跳重检、限制协议/端口和可替换 transport 降低风险；生产出网隔离和 egress allowlist 作为部署层补充，不由应用代码替代。

## 7. 实施结果

- 新增默认拒绝的 `ToolRegistry`、唯一公开执行入口 `ToolPolicyGateway`、固定 `ToolPolicy`、脱敏 `ToolCallAuditEvent` 与稳定错误分类；Gateway 会先拒绝未授权或未注册 Tool，再调用 Adapter，审计目标只保留 origin 与路径哈希，不含查询参数、请求头或响应正文。
- 新增 `UrlSafetyValidator` 与可替换 `HostResolver`，要求 HTTPS、无 userinfo、默认 443 端口，并拒绝 loopback、private、link-local、multicast、unspecified、CGNAT 与 IPv6 ULA 地址。初始目标、每次重试和每次重定向都会重新校验。
- 新增 `WebReaderToolAdapter`、`WebTransport`、`JdkWebTransport` 和 `WebResponse`。JDK transport 禁用自动重定向、受连接/请求超时与流式字节上限控制；Adapter 仅接受 `text/html`/`text/plain`，移除脚本、样式、表单等非可见 HTML 内容，受限输出文本、标题与 SHA-256 哈希。它尚未注册为 Spring Bean 或接入主 Workflow。
- 新增 `ToolPolicyGatewayContractTest`（4 项），以 Fake DNS/HTTP 覆盖未授权/未注册拒绝、HTTP 协议拒绝、私网重定向二跳阻断、可重试 503、HTML 规范化、超大响应与非文本 MIME。未访问公网。
- 验证：定向测试 4 项通过；默认 `mvn test` 首次主代码增量编译受已知 Windows 编译器资源关闭问题影响，未改源码重跑后 Surefire 当前命名空间 49 项、0 失败、0 错误。`git diff --check` 将在本 E 的全部文档更新后执行。
