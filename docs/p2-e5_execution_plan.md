# P2 E5：版本化 Skill Registry 与 P2 收口执行计划

> 状态：已完成
> 日期：2026-08-29
> 依据：[p2_execution_plan.md](p2_execution_plan.md)、[p2-e4_execution_plan.md](p2-e4_execution_plan.md) 与当前代码。

## 1. 目标与范围

交付仓库内置、声明式且不可变的项目层 Skill Registry，为 P3 的 Writer/Reviewer 提供稳定的 Skill 契约。Registry 只解析和校验 Skill，不执行动态代码、不直接调用模型或 Tool、不修改 Run/checkpoint；实际执行仍必须经 Workflow、ResearchUseCase 与 ToolPolicyGateway。

本 E 交付五个内置声明：`research-brief`、`longform-article`、`fact-check`、`visual-plan`、`citation-format`。框架 `ReactAgent.read_skill` 的按需内容加载 API 未在当前依赖版本完成独立兼容验证，因此不在本 E 伪造或绑定该 API；项目层 Registry 是后续适配的唯一契约来源。

## 2. 设计

- `SkillDefinition` 固定 id、版本、输入/输出 Schema、Tool 白名单、最大 Tool 调用数和验收条件；定义在构造期完成防御性复制。
- `SkillSchema` 仅声明结构化字段名与必填字段，用于拒绝缺失/未知字段；它不是运行时脚本或自由 JSON Schema 解释器。
- `SkillRegistry` 用 `(skillId, version)` 精确解析，注册时拒绝同版本重定义，因而已发布定义不可被静默覆盖。
- `SkillContractValidator` 在执行前校验输入、请求 Tool 和预算，在输出交接前校验输出 Schema。Tool 白名单以 `ToolId` 表达，后续真实 Tool 仍必须通过 Gateway 的二次授权。
- `BuiltinSkillCatalog` 提供五个版本 `1.0.0` 的声明；Spring 只装配 Registry/Validator，不将 Skill 直接暴露为模型或 HTTP 执行入口。

## 3. 测试矩阵

| 场景 | 断言 |
|---|---|
| 内置目录 | 五个 Skill 均可按精确版本解析 |
| 不可变版本 | 重复注册同一 id/version 被拒绝 |
| 输入/输出 Schema | 缺失或未知字段被拒绝 |
| Tool 授权 | 请求未声明 Tool 被拒绝 |
| 预算 | 超过 Skill 最大调用数被拒绝 |
| Spring 装配 | 可注入 Registry 与 Validator，且不触发网络/模型调用 |

## 4. 验证与回滚

- 默认 `mvn test` 必须保持离线；`git diff --check` 通过。
- P2 E1–E4 的 Tool/来源/持久化 Testcontainers 回归必须不受影响。
- 回滚只删除 Registry、Catalog 与其 Spring Bean；既有来源、审计、Run 和 Tool Gateway 数据不受影响。

## 5. 实施结果

- 新增 `SkillSchema`、`SkillDefinition`、`SkillRegistry`、`SkillContractValidator` 与 `BuiltinSkillCatalog`。Registry 按 `(id, version)` 精确解析，拒绝重复注册；Schema 拒绝缺失、未知或空值字段；Validator 拒绝越权 Tool 和超出 Skill 声明的调用预算。
- 已声明五个 `1.0.0` 内置 Skill：`research-brief`、`longform-article`、`fact-check`、`visual-plan`、`citation-format`。声明保留可消费的结构化输入/输出字段及验收条件，但不直接执行模型/HTTP/Tool。
- `AgentConfig` 装配 Registry 与 Validator；真实 Tool 执行仍经 P2 E2 Gateway，Skill 尚未直接暴露为应用 API。`ReactAgent.read_skill` 继续作为未来独立框架兼容验证项。
