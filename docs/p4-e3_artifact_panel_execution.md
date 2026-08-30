# P4 E3：Artifact 与版本交付面板

状态：已完成  
依赖：P3 `agent_article_version` / `agent_artifact` 的 append-only 语义，以及 P4 E2 的授权 Run read-model。  
范围：将已登记的版本与 Artifact Manifest 以只读、安全方式展示在文章详情。不得将 `run://` 逻辑地址误称为已上传或可下载对象。

## 已确认事实与设计

- P3 的 `agent_artifact` 只保存 `artifactId/type/location/sha256`；其中 location 是逻辑地址，表中没有对象存储 key、二进制内容或签名 URL。
- 因此 E3 新增安全 Manifest API 和版本面板：显示版本链、修改原因、交付物类型、哈希、登记时间和交付状态。
- 下载入口的规则固定如下：只有服务端能够验证并生成内容的交付物才显示下载；当前 P3 逻辑 `run://` 记录统一显示“已登记，尚未有可下载对象”，禁止前端拼接、跳转或重定向该地址。
- P3 以后若 Artifact 写入 COS/受控本地导出，必须新增单独阶段文档，记录对象所有权、短期签名 URL、content-type、审计和权限校验；不得把其混入 E3。

## API 与 UI 契约

| 接口/模块 | 契约 |
|---|---|
| `GET /api/agent-runs/{runId}/artifacts` | 经文章权限校验，返回版本链和对应 Manifest 的安全投影；不返回 draftsSnapshot 或任意对象存储 URL |
| `AgentArtifactPanel.vue` | 按版本展示 Artifact，基于 `downloadable` 显示下载状态；只渲染文本，不使用 location 作为链接 |

## 测试、回滚与非目标

- MVC：未授权拒绝；响应不含 `draftsSnapshot`；所有 `run://` Artifact 的 `downloadable=false`。
- 前端：逻辑地址不是可点击链接，空版本显示“暂无已登记交付物”。
- 回滚只移除只读面板/API；不删除版本或 Artifact 记录。
- E3 不实现对象上传、下载、版本内容 diff、来源全文或质量报告正文；这些需要有真实受控对象后另行实施。

## 实施结果

- 已新增授权保护的 Manifest API 与文章详情 Artifact/版本面板；响应不包含 draftsSnapshot，逻辑 `run://` 地址不作为链接输出。
- 所有当前 P3 交付物显式标记为不可下载，避免将未上传对象伪装为可下载文件。
- 后端编译和前端生产构建已通过。
