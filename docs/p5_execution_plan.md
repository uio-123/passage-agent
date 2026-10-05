# P5：评测、工程化与简历交付执行计划

状态：`DRAFT`（D0、E2–E5 工程实现已完成本地验证；E1 的 30 条数据仍为 DRAFT，待双人复核与裁决；所有报告均为 `DRAFT/NON_RELEASE`）
前置条件：P1–P4 已提供 Run/checkpoint、受控研究、质量闭环、事件和模型调用测量；P3/P4 的默认 feature flag 与安全边界保持不变。
目标：建立可重复、可审计的证据链，用固定数据和明确公式验证质量、可靠性、性能与成本；README 和简历只引用已经生成并入库的报告。

## 1. 不可变执行原则

- **先协议、后测量、最后宣传。** 本文档和机器可读 Schema 必须先于首次正式评测；门槛不得在看到结果后静默下调。
- **确定性门禁与模型评测分离。** PR 门禁只使用 Fake Model、固定 Fixture、Mock HTTP 与 Testcontainers，不需要真实 Key 或公网。真实 LiteLLM 评测只可人工触发或在受控发布环境运行。
- **比较变量唯一。** 同一批次的三个工作流变体必须使用相同数据集版本、模型路由、模型参数、价格表、并发度和重放研究包；除了被比较的评审/返工能力，不得同时更换 Prompt 或模型。
- **LLM Judge 不是发布裁判。** Judge 只用于排序和发现候选问题；事实、引用、状态、恢复和安全门禁由确定性校验与人工标注决定。
- **结果不可覆盖。** 每次正式运行写入独立的 `run-id` 目录；原始清单、逐样本结果、汇总和环境指纹一起保存。修正评分器后创建新运行，不改旧结果。
- **不伪造不可得指标。** usage 缺失时 Token/成本记为 `null` 和 `UNAVAILABLE`，不得以字符数估算；没有实现的上下文策略不得生成对比行。
- **安全最小化。** 数据集只保存合成或授权内容、规范化事实点和可回放的短研究摘录；禁止保存真实用户正文、Cookie、认证头、密钥或受版权限制的完整网页。

## 2. 阶段与严格交付顺序

| 阶段 | 交付物 | 实施范围 | 进入下一阶段的门禁 |
|---|---|---|---|
| D0 | 本文档、文档索引、`plan.md` 状态 | 固定数据集、公式、阈值、CI、压测、Docker 和 README 顺序 | 文档中不存在待实现前就宣称的结果 |
| E1 | `evaluation/datasets/p5-v1/`、Schema、校验器与 30 条数据 | 数据契约、分层覆盖、人工标注模板、版本与哈希 | 30/30 样本通过 Schema、唯一性、分层和安全校验 |
| E2 | 评测运行器、三变体适配、版本化价格表、报告 | 逐样本确定性评分、人工评分导入、汇总、基线对比、Skill 回归 | 固定 Fake 结果可重复；缺失数据不被计为 0；报告可由命令重建 |
| E3 | `.github/workflows/ci.yml` 与分层脚本 | 后端快速测试、前端构建、Docker/Testcontainers 集成、Compose 验证 | PR 层完全离线；Docker 层不允许因环境缺失静默跳过 |
| E4 | `performance/` 场景、执行器与报告模板 | 确定性并发基线、故障恢复、可选真实模型容量测试 | 固定环境重复 3 次；逐次与聚合报告齐全；副作用一致性通过 |
| E5 | Docker 演示覆盖文件、演示 Fixture、健康/冒烟脚本 | 无真实 Key 的确定性演示；可选真实 LiteLLM 演示保持显式配置 | 新目录克隆后可按单一命令启动并通过健康/演示断言 |
| E6 | README、演示图/GIF、最终报告、面试材料 | 只引用 E1–E5 已验证命令、能力、限制和数字 | README 冷启动复核通过；每个量化数字可追溯到报告和 run-id |

每个阶段先增加该阶段的自动验证，再修改实现。经 2026-08-31 用户确认，E1 人工复核不再阻塞 E2–E5 的工程实现，但继续阻断正式评测、基线接受和任何宣传数字；E6 只可更新已经验证的命令与能力，不能填写收益百分比、吞吐或延迟数字。

## 3. E1：离线评测集定义

### 3.1 数据集规模与冻结规则

正式数据集固定为 **30 条**，版本为 `p5-v1`。30 条是首版可完成人工复核且能覆盖主要分层的最小规模，不把它包装成统计上代表全部中文写作场景的大样本。

| 维度 | 分层 | 数量 |
|---|---|---:|
| 主题 | 科技 / 教育 / 情感 | 各 10 |
| 路由 | 需要研究 / 免研究 | 18 / 12 |
| 配图 | 需要配图 / 不需要配图 | 18 / 12 |
| 质量闭环预期 | 首轮接受 / 需要局部返工 | 18 / 12 |
| 可靠性场景 | 正常 / 可恢复故障注入 | 24 / 6 |

这些维度允许正交组合；校验器验证各分层总数，而不是把 30 条复制成多套样本。样本 ID 固定为 `p5-<domain>-NN`，发布后内容变更必须创建 `p5-v2`，不得原地修改 `p5-v1`。

### 3.2 单样本机器可读契约

每条 JSON 样本至少包含：

- `id`、`datasetVersion`、`domain`、`language`、`title`、`userGoal`、`style`；
- `requiresResearch`、`requiresImages`、`expectedRoute`、`faultScenario`；
- `outlineRequirements`：必需章节、禁止章节、最少/最多章节；
- `referenceFacts`：原子事实 ID、规范化陈述、允许来源 ID、重要性权重 `1|2`；
- `replaySources`：来源 ID、规范 URL、标题、短摘录、抓取时间、内容 SHA-256；
- `requiredCitationFactIds`：必须有引用覆盖的事实点；
- `styleRubric`、`readabilityRubric`、`forbiddenClaims`、`expectedArtifacts`；
- `fakeResponses`：固定 Writer/Reviewer/Revision 响应或引用路径，不包含真实模型输出；
- `annotationStatus`：`DRAFT|DOUBLE_REVIEWED|ADJUDICATED` 和匿名标注者编号。

研究样本必须有可回放来源；免研究样本的 `replaySources` 必须为空且不得因没有引用被扣“引用有效率”。最终正式报告只纳入 `ADJUDICATED` 样本。

### 3.3 人工标注与抽检

- 30 条的事实点、允许来源与禁用断言必须 **100% 双人复核**；分歧由第三次裁决形成 `ADJUDICATED` 状态。
- 每次正式模型评测对 **至少 30% 样本（向上取整为 9 条）**做人工成品复核，按主题分层抽取且每个主题不少于 3 条。
- 三个变体的成品以盲化随机顺序评分；标注者看不到变体名称、Token、成本和 Judge 分。
- 若 9 条中任意 2 条出现确定性评分器未发现的严重事实错误、伪引用或隐私泄漏，本批次失败，并扩展到 30 条全量人工复核。

### 3.4 数据集验收

- Schema、ID、来源 ID、事实 ID、SHA-256 和分层计数校验全部通过；
- 18 条研究样本至少各有 2 个独立来源和 3 个原子事实点；
- 12 条免研究样本明确审计“未请求事实增强”，不得提供伪来源；
- 数据集中不出现密钥模式、认证头、Cookie、真实用户标识或超出短摘录范围的网页正文；
- `dataset-manifest.json` 记录版本、样本数、文件哈希、创建时间、许可说明和变更摘要。

## 4. E2：指标公式、门槛与对比协议

### 4.1 记号与聚合约定

- `N`：本批次有效样本数；正式报告必须为 30，试运行必须标为 `NON_RELEASE`。
- 比率先在每条样本内计算，再对样本做宏平均，避免事实点多的长文支配结果。
- P50/P95 使用 nearest-rank：排序后取 `ceil(p × n)` 位；同时输出样本数、min、max，不在 `n < 20` 时把 P95 描述为稳定总体估计。
- 所有比例以原始分子/分母和百分比同时输出；分母为 0 时记 `NOT_APPLICABLE`，不记 100%。
- 比较结果同时给绝对差值和相对差值；基线为 0 时只报告绝对差值。

### 4.2 质量与可信度公式

1. **事实支持率（FSR）**

   对样本 `i`，人工标注或确定性匹配将输出中的原子事实声明分类为 `SUPPORTED`、`UNSUPPORTED`、`CONTRADICTED`。纯观点、修辞和明确虚构内容不进入分母。

   `FSR_i = Σ supported_claim_weight / Σ eligible_claim_weight`

   重要性权重只允许 1 或 2，并来自冻结数据集。批次值 `Macro-FSR = mean(FSR_i)`。

2. **引用有效率（CVR）**

   仅对研究样本计算。一个引用同时满足“来源 ID 存在、URL 与回放来源一致、该来源支持紧邻事实”才有效。

   `CVR_i = valid_citation_occurrences / all_citation_occurrences`

   另报引用覆盖率：`CCR_i = cited_required_fact_ids / required_citation_fact_ids`。无引用但存在必引事实时，`CVR_i = 0` 且 `CCR_i = 0`。

3. **结构完整度（SC）**

   `SC_i = satisfied_required_outline_items / all_required_outline_items × 100`

   出现任一禁止章节时，每项额外扣 10 分，最低为 0；确定性标题/章节解析规则版本写入报告。

4. **风格一致性（ST）与可读性（RD）**

   人工按冻结 Rubric 对 5 个条目分别给 `0/1/2`；`ST_i`、`RD_i` 各自换算为 0–100。Judge 可给建议分，但不能覆盖人工分。

5. **综合质量分（QS）**

   `QS_i = 0.30×FSR_i×100 + 0.20×CitationComponent_i + 0.20×SC_i + 0.15×ST_i + 0.15×RD_i`

   研究样本 `CitationComponent = 0.6×CVR×100 + 0.4×CCR×100`；免研究样本不虚构引用分，权重重分配为 `FSR 40%、SC 25%、ST 17.5%、RD 17.5%`。

   若某项分母为 0（例如免研究创作没有可判定事实声明），该项保持 `NOT_APPLICABLE`，不按 0 分处理；QS 按其余适用项的权重和重新归一化，并同时报告 `componentCoverage = 适用权重 / 原始权重`。缺少本应存在的人工风格/可读性评分属于 `MISSING` 而非 `NOT_APPLICABLE`，此时只输出 `provisionalQualityScore`，正式 `qualityScore` 保持 `null`。

6. **严重缺陷覆盖规则**

   任一伪造来源、与来源相矛盾的关键事实、敏感数据泄漏、无限返工或未授权 Tool 调用，都使该样本 `criticalFailure=true`；综合分不得抵消严重缺陷。

### 4.3 性能、成本、稳定性与协作公式

- 端到端延迟：`completedAt - startedAt`；失败任务单独计失败延迟，不混入成功任务分位数。
- 首 Token 延迟：模型调用开始到首个非空流式 chunk；非流式或未采集记 `NOT_AVAILABLE`。
- 并行加速比：`sum(section_execution_ms) / parallel_wall_clock_ms`；同时报告并发度，禁止拿不同任务集比较。
- 输入/输出 Token：只汇总实际 response metadata；任一调用缺 usage 时样本 Token/成本状态为 `PARTIAL`。
- 估算成本：`Σ(input_tokens/1e6×input_price + output_tokens/1e6×output_price)`；价格来自带生效日期和币种的版本化价格表，并明确标为估算。
- 成功率：`terminal_success_runs / all_started_runs`。
- 恢复成功率：`successfully_completed_injected_runs / all_recoverable_injected_runs`。
- 重复副作用率：`duplicate_external_side_effects / all_external_side_effect_attempts`。
- 返工后提分：同一章节 `post_revision_score - pre_revision_score` 的平均值；未返工章节保持率：`byte_identical_unmarked_sections / all_unmarked_sections`。
- 质量边际成本：`(full_variant_cost - no_review_cost) / (full_variant_QS - no_review_QS)`；质量未提升时只报告“无正向边际收益”，不输出误导性负成本。

### 4.4 正式门槛

门槛分为“发布硬门禁”和“对比目标”。硬门禁任一失败时不得在 README/简历宣称 P5 通过。

| 类别 | P5-v1 硬门槛 |
|---|---|
| 数据完整性 | 30/30 为 `ADJUDICATED`；Schema 和安全扫描 100% 通过 |
| 执行完整性 | 完整多 Agent 变体至少 29/30 到达预期终态；所有失败有受控错误码 |
| 严重缺陷 | 0 个伪造来源、0 个敏感数据泄漏、0 个未授权 Tool、0 个无限循环 |
| 事实支持 | 完整变体 Macro-FSR ≥ 0.90，且任一主题分层 ≥ 0.85 |
| 引用 | 研究样本 Macro-CVR ≥ 0.95、Macro-CCR ≥ 0.90 |
| 综合质量 | 完整变体 QS 中位数 ≥ 80，且至少 24/30 样本 QS ≥ 75 |
| 人工复核 | 盲评样本中 0 个严重缺陷；人工与确定性核心结论不冲突 |
| 恢复与幂等 | 6/6 故障样本恢复成功；重复副作用率 = 0 |
| Skill 回归 | 同数据/模型下 Macro-FSR、CVR、QS 均不得下降超过 2 个绝对百分点；严重缺陷仍为 0 |
| 数据可得性 | Token/成本为 `COMPLETE` 才可给出成本结论；否则只报告覆盖率和不可得原因 |

完整多 Agent 相对“无评审版本”的对比目标为：QS 至少提高 3 个绝对分，或 Macro-FSR 至少提高 3 个百分点；同时 Token 增幅不超过 80%、成功任务 P95 延迟增幅不超过 100%。这是首轮工程目标，不允许通过删除失败样本达成；若质量目标未达成，保留报告并进入调优，不修改门槛。

### 4.5 三变体和上下文策略

正式工作流对比固定为：

- `legacy`：当前原始正文流水线；
- `multi_agent_no_review`：章节并行写作，但跳过 Fact/Style Review 与 Revision；
- `multi_agent_full`：章节写作、双 Reviewer、Quality Gate、最多两轮局部 Revision 和 Artifact。

上下文策略作为独立实验轴：`full_history`、`selective`、`summary_compressed`。当前代码只具备安全 Snapshot，不具备可替换的 Context Manager，因此 E2 首轮报告必须把该实验标为 `NOT_IMPLEMENTED`。只有三种策略共享统一接口、Token 可测且契约测试通过后，才能运行 3×3 矩阵；不得用截断字符串冒充摘要压缩。

## 5. E3：CI 分层

| 层 | 触发 | 内容 | 网络/密钥 | 超时目标 | 阻断规则 |
|---|---|---|---|---|---|
| L0 静态与数据 | 每个 PR | Compose config、评测 Schema/分层/安全校验、脚本自测 | 禁止 | 3 分钟 | 任一失败阻断 |
| L1 快速回归 | 每个 PR | JDK 21 `mvn test`；Node LTS `npm ci && npm run build` | 禁止真实服务 | 12 分钟 | 任一测试/类型/构建失败阻断 |
| L2 持久化集成 | 每个 PR / 合并前 | `mvn test -Ppersistence-integration`，真实 MySQL Testcontainers | 只允许拉取固定容器镜像 | 15 分钟 | Docker 缺失视为基础设施失败，不得静默跳过 |
| L3 Compose 演示冒烟 | 主分支 / 发布候选 | 构建镜像、启动确定性 demo、健康检查、最小业务断言 | 不需要真实 LLM Key | 20 分钟 | 任一服务不健康或断言失败阻断发布候选 |
| L4 真实集成 | 人工 / 发布前 | `litellm-smoke`、30 条正式评测 | 受控 Key | 预算控制 | 不阻断普通 PR；阻断量化结论发布 |
| L5 性能 | 人工 / 里程碑 | E4 固定并发矩阵与故障注入 | Fake 默认；真实模型可选 | 独立执行 | 阻断性能/可靠性数字发布 |

CI 必须固定 JDK 21、Node 版本、Maven 参数和 npm lockfile。L1 与 L2 分 Job，便于区分代码失败和 Docker 基础设施失败。日志和 Artifact 不上传密钥、Prompt、正文或网页全文；正式报告仅保存安全汇总与哈希。

## 6. E4：压测范围与判定

### 6.1 两类场景

1. **确定性容量基线（CI 可复现）**：Fake Model + 回放研究 + 本地 Artifact，测调度、checkpoint、事件、持久化和恢复，不声称代表真实模型吞吐。
2. **真实模型容量观察（人工）**：固定 LiteLLM 路由和预算，测真实 Token、成本、首 Token 与限流表现，只与同日、同模型、同参数结果比较。

### 6.2 固定负载矩阵

- 环境指纹：OS、CPU 核数、内存、JDK、Docker、镜像摘要、Git SHA、数据库配置；未记录环境指纹的结果无效。
- 并发任务数：`1 / 4 / 8 / 16`；每档预热 30 秒、测量 3 分钟、冷却 30 秒，独立重复 3 次。
- 任务混合：60% 正常研究/写作，20% 免研究，10% Reviewer 首次失败后返工，10% 可恢复故障。
- 故障注入：模型超时、单章节失败、checkpoint 后进程恢复、重复 continue 请求；不在首版注入真实 MySQL 数据损坏或网络分区。
- 输出：每档成功/失败数、吞吐（completed runs/min）、成功延迟 P50/P95、恢复延迟 P50/P95、节点重试率、重复副作用率；真实模型场景另报 usage 覆盖率、Token 和估算成本。

### 6.3 压测门槛

- 确定性场景成功率 ≥ 99%，所有可恢复故障恢复成功，重复副作用率 = 0；
- 在并发 8 时，三次运行的吞吐中位数不得低于单并发线性外推值的 50%；
- 同一固定环境相对已接受基线，成功延迟 P95 回退不超过 20%，吞吐回退不超过 10%；
- 并发 16 允许暴露容量拐点，但不得出现状态错乱、越权、死循环或不可解释的数据丢失；
- 真实模型场景成功率目标 ≥ 95%；供应商限流单独分类，不能与应用错误合并或从分母删除。

绝对毫秒阈值在首个固定环境基线前不设定，避免把开发机性能写成通用承诺。首个被接受的报告通过独立基线清单冻结，后续只按同环境比较。

## 7. E5：Docker 一键演示

### 7.1 交付形式

- 保留生产 `docker-compose.yml`；新增 `docker-compose.demo.yml` 作为显式覆盖，不让 demo stub 混入生产 profile。
- 新增 `demo` Spring profile，仅装配确定性场景端点；该 profile 默认关闭，并要求显式的非生产确认。Demo Compose 在生产配置基线上叠加该 profile，但使用独立容器、网络和数据卷，且不会调用模型、搜索或图片服务。
- 新增 `.env.demo.example`，只含非敏感默认值；演示不要求 LiteLLM、Pexels、COS 或 Stripe Key。
- 演示启动命令固定为一个跨平台 Compose 命令；随后由脚本完成健康检查，并让四类确定性场景经过真实 Run/Event/ArticleVersion/Artifact 持久化边界，验证恢复与幂等。它不是旧文章 HTTP 主链路或真实模型质量的替代证明。
- 演示数据必须幂等：同一 seed 重跑不重复创建；清理使用独立命名 volume，文档明确 `down` 与 `down -v` 的差异。

### 7.2 演示验收

- `docker compose -f docker-compose.yml -f docker-compose.demo.yml config --quiet` 通过；
- MySQL、Redis、backend、frontend 全部 healthy 后才执行断言；
- 从空 demo volume 启动，健康检查在 5 分钟内通过；
- 最小业务链路返回 `COMPLETED`、安全事件、一个版本和三个 Artifact，故障场景可恢复；同一场景重放不得新增事件或副作用；
- 关闭 demo profile 后，应用仍要求正常的真实服务配置，且仓库运行时扫描找不到硬编码 Key。

## 8. E6：README 与最终交付顺序

README 只能按以下顺序更新，避免“代码未验收，文档先宣传”：

1. E3 完成后更新“本地测试与 CI 分层命令”；
2. E5 完成后更新“5 分钟 Docker 确定性演示”和故障排查；
3. E2 首次正式报告通过后增加“评测协议与结果”链接，先写样本数、模型、日期和限制；
4. E4 基线通过后增加性能表，必须附环境指纹和 run-id；
5. 最后制作 Demo 截图/GIF、真实架构图和面试材料；截图不得显示 Key、Prompt、用户敏感信息或虚假成本；
6. 冷启动复核者只依据 README 在新目录完成启动、测试和报告定位，问题修正后才标记 P5 完成。

最终证据链为：`README 数字 → evaluation/performance 汇总 → run manifest → dataset/model/pricing/git/environment 版本`。任何一环缺失，该数字不得用于简历。

## 9. 目录约定

```text
evaluation/
├── README.md
├── schemas/
├── datasets/p5-v1/
├── pricing/
├── src/                  # 评测器或薄适配层
└── reports/<run-id>/     # manifest、samples、summary；正式报告可入库
performance/
├── README.md
├── scenarios/
├── baselines/
└── reports/<run-id>/
scripts/
├── ci/
└── demo/
```

临时模型正文、完整 trace 和未脱敏原始输出写入被忽略的工作目录，不进入 Git。正式入库报告只包含评分、计数、受控错误码、哈希和不超过必要长度的诊断摘要。

## 10. 回滚与变更控制

- E1/E2 是旁路评测工具，不改变生产状态机；回滚删除调用入口即可，已生成报告保留审计。
- E3 先以新 workflow 引入；若 Runner 基础设施故障，修复 workflow，不通过排除测试降低门禁。
- E4 不向生产 API 增加无认证压测入口；需要测试端点时只允许 demo/test profile 且启动时显式校验。
- E5 demo profile 是受确认开关保护的附加 profile；回滚只移除 override/profile，不修改生产数据或迁移。生产部署不得设置该确认变量或启用 `demo`。
- 指标公式、数据集或门槛的任何变更必须提升协议版本并记录原因、影响和新旧结果不可直接比较之处。

## 11. P5 完成定义

- E1–E6 全部通过，30 条 `p5-v1` 数据与机器可读协议可由固定命令校验；
- PR 的离线 L0/L1 和 Docker L2 门禁稳定运行，L3 可从空环境完成确定性演示；
- 至少一份三变体正式评测报告和一份固定环境并发报告可复建，未实现的上下文实验明确标注而非补假数据；
- README 中每个命令已执行，每个数字带样本量、模型/环境、日期、run-id 和限制；
- `docs/development_log.md` 记录各阶段决策、验证结果、失败案例和修复，`docs/plan.md` 与实际状态一致。

## 12. 当前实施状态（2026-08-31）

- E1：30 条合成数据、Schema、哈希校验和双审/裁决工具已完成；人工复核尚未填写，正式发布门禁保持关闭。
- E2：三变体评分、Skill 回归、版本化测试价格与不可覆盖报告已实现；仅生成评分器自测的 `DRAFT/NON_RELEASE` 报告。
- E3：L0/L1/L2 分层与主分支 L3 Compose Demo 已写入 GitHub Actions；L4/L5 保持人工触发命令，不进入普通 PR。
- E4：完整 `1/4/8/16 × 3` 场景和短时链路冒烟场景已实现；短时报告只证明执行器与链路可用，`acceptedBaselineRegression=null`，不能作为性能基线。
- E5：隔离 Compose 覆盖、启动保护、四类确定性场景和冒烟脚本已完成本地容器验证。
- E6：README 只更新了 CI、Demo、协议入口和限制；正式评测结果、性能表、宣传数字与素材仍锁定。
