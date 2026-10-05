# P5 离线评测

本目录实现 `docs/p5_execution_plan.md` 中的机器可读评测协议。`p5-v1` 当前是 30 条纯合成、可离线回放的样本草案，不含真实用户数据或网页全文。

## 当前状态

- Schema、分层、ID、来源/事实引用关系和安全约束由 `EvaluationDatasetContractTest` 校验。
- `dataset-manifest.json` 的 `releaseReady=false`，30 条样本均为 `DRAFT`。
- 在 100% 双人复核并完成分歧裁决前，不得生成标为正式的 P5 质量报告，也不得在 README 或简历引用质量数字。

## 校验命令

```bash
mvn -q -Dtest=EvaluationDatasetContractTest test
node --test evaluation/tools/review-workflow.test.mjs
node --test evaluation/tools/score-run.test.mjs evaluation/tools/compare-skill.test.mjs
```

## DRAFT/NON_RELEASE 评分器

在人工裁决前可以运行评分器自测，但输出会同时标记 `reportStatus=DRAFT` 和 `publicationStatus=NON_RELEASE`：

```bash
node evaluation/tools/score-run.mjs evaluation/datasets/p5-v1/samples.json evaluation/datasets/p5-v1/dataset-manifest.json evaluation/pricing/fixture-model-v1.json evaluation/fixtures/draft-scorer-input.json evaluation/reports/draft-scorer-selftest-v2
```

评分器输出逐样本 FSR/CVR/CCR/结构/风格/可读性/QS、usage 可得性、成本、延迟、恢复与副作用指标，并生成三个变体的宏平均、nearest-rank P50/P95 和收益/Token/延迟代价对比。缺失 usage 保持 `null`，不会被写成 0。报告目录不可覆盖，同一 run-id 重跑会失败。

`--release` 默认不可用：只有数据 Manifest 已解锁、30 条全部 `ADJUDICATED`、三个变体 90 个单元齐全、人工成品抽检满足 9 条且每个主题不少于 3 条、质量组件完整时才能生成正式报告。当前 fixture 使用测试价格和自测数据，即使人工数据门禁以后解锁也不能转为正式结果。

Skill 版本回归使用两份 summary 比较，允许 FSR/CVR/QS 最多下降 2 个绝对百分点且不允许严重缺陷：

```bash
node evaluation/tools/compare-skill.mjs <baseline-summary.json> <candidate-summary.json>
```

## CI 分层入口

L0 的本地等价命令会运行全部评测工具测试、Java 数据集契约和生产 Compose 结构校验：

```bash
node scripts/ci/run-l0.mjs
```

GitHub Actions 将 L0、后端离线回归、前端构建和 MySQL Testcontainers 分为独立 Job；主分支另运行隔离 Compose Demo。Docker 不可用会使 L2/L3 明确失败，不会静默跳过；真实 LiteLLM、Pexels 或其他外部 Key 不进入 PR 工作流。完整压测矩阵位于独立、仅人工触发的 `performance.yml`，上传工件仍是 `DRAFT/NON_RELEASE`。

## 双人复核与裁决

两位复核者必须独立从同一 `samples.json` 生成各自标注文件，不能共享一份文件。工作文件默认放在被 Git 忽略的 `evaluation/work/`：

```bash
node evaluation/tools/review-workflow.mjs prepare evaluation/datasets/p5-v1/samples.json reviewer-a evaluation/work/reviewer-a.json
node evaluation/tools/review-workflow.mjs prepare evaluation/datasets/p5-v1/samples.json reviewer-b evaluation/work/reviewer-b.json
```

每位复核者需要逐项确认事实是否与回放材料一致、权重是否合适、允许来源是否支持事实、摘录是否安全充分、禁用断言是否合适，并填写样本结论与 `reviewedAt`。完成后分别校验：

```bash
node evaluation/tools/review-workflow.mjs validate-review evaluation/datasets/p5-v1/samples.json evaluation/work/reviewer-a.json
node evaluation/tools/review-workflow.mjs validate-review evaluation/datasets/p5-v1/samples.json evaluation/work/reviewer-b.json
```

只有两份完整且 reviewer ID 不同的复核文件才能生成裁决草案：

```bash
node evaluation/tools/review-workflow.mjs adjudicate evaluation/datasets/p5-v1/samples.json evaluation/work/reviewer-a.json evaluation/work/reviewer-b.json evaluation/work/adjudication.json
```

一致项会自动带入；分歧项的 `resolution` 保持 `null`，必须由裁决者填写 `resolution`、`rationale`、`adjudicatorId` 和 `adjudicatedAt`。最终执行：

```bash
node evaluation/tools/review-workflow.mjs validate-adjudication evaluation/datasets/p5-v1/samples.json evaluation/work/reviewer-a.json evaluation/work/reviewer-b.json evaluation/work/adjudication.json
```

工具把数据集和两份复核文件的规范化 SHA-256 写入证据，任一输入在复核后变化都会使校验失败。校验成功仍不会自动修改受版本控制的数据；提升 `annotationStatus` 和 `releaseReady` 必须作为独立、可审查变更执行。

若所有最终 resolution 均为 `true` / `APPROVE`，可生成发布候选；任何否决项都会阻止生成，必须先修改数据并重新走两次独立复核：

```bash
node evaluation/tools/review-workflow.mjs promote evaluation/datasets/p5-v1/samples.json evaluation/datasets/p5-v1/dataset-manifest.json evaluation/work/reviewer-a.json evaluation/work/reviewer-b.json evaluation/work/adjudication.json evaluation/work/samples.promoted.json evaluation/work/dataset-manifest.promoted.json
```

该命令只写入被忽略的 `evaluation/work/`。候选把 30 条样本标为 `ADJUDICATED`，记录三位匿名标注者和复核证据哈希，并重新计算样本 SHA-256；合入正式目录前仍须人工审查 diff。

## 目录

```text
schemas/p5-sample.schema.json       样本 JSON Schema
schemas/p5-review.schema.json       独立复核 Schema
schemas/p5-adjudication.schema.json 裁决 Schema
datasets/p5-v1/samples.json         30 条合成样本
datasets/p5-v1/dataset-manifest.json 数据集版本、哈希与发布门禁
tools/review-workflow.mjs           复核、裁决与哈希校验 CLI
```

研究样本使用 `.invalid` 域名与合成短摘录，目的是稳定验证引用约束和评分器，不代表对现实世界事实的背书。
