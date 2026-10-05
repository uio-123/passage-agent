# 项目文档索引

| 文档 | 用途 |
|---|---|
| [plan.md](plan.md) | 全项目阶段规划、优先级与验收标准 |
| [p0-b_execution_plan.md](p0-b_execution_plan.md) | 当前 P0-B 的可执行任务、门禁与回滚方案 |
| [p0-b-e3_execution_plan.md](p0-b-e3_execution_plan.md) | P0-B E3 统一图迁移的范围、验收与回滚方案 |
| [p1_execution_plan.md](p1_execution_plan.md) | P1 Supervisor、条件路由、持久化恢复与幂等的执行任务、门禁与回滚方案 |
| [p1.5_execution_plan.md](p1.5_execution_plan.md) | P1.5 去模板化、身份迁移、兼容边界与验证方案 |
| [p2_execution_plan.md](p2_execution_plan.md) | P2 受控工具、可追溯研究与 Skills 的执行任务、门禁与回滚方案 |
| [p2-e2_execution_plan.md](p2-e2_execution_plan.md) | P2 E2 Policy Gateway 与安全 Web Reader 的接口、安全测试、验证与回滚方案 |
| [p2-e3_execution_plan.md](p2-e3_execution_plan.md) | P2 E3 来源与 Tool 审计持久化的数据模型、测试、验证与回滚方案 |
| [p2-e4_execution_plan.md](p2-e4_execution_plan.md) | P2 E4 Research Agent 与 Supervisor 真实研究接入的执行与回滚方案 |
| [p2-e5_execution_plan.md](p2-e5_execution_plan.md) | P2 E5 版本化 Skill Registry 的接口、验证与回滚方案 |
| [p3_execution_plan.md](p3_execution_plan.md) | P3 并行写作、质量闭环与 Artifact 的分步执行方案 |
| [p3-e6_stategraph_migration_plan.md](p3-e6_stategraph_migration_plan.md) | P3 质量闭环接入旧文章 StateGraph 主流程的迁移、验证与回滚方案 |
| [p3-e6-e2_runner_branch_execution.md](p3-e6-e2_runner_branch_execution.md) | P3 E6.2 Runner feature-flag 分支的可执行清单与回滚边界 |
| [p3-e6-e3_checkpoint_sse_execution.md](p3-e6-e3_checkpoint_sse_execution.md) | P3 E6.3 checkpoint、SSE 与恢复兼容的快照契约、测试和回滚清单 |
| [p3-e6-e4_image_delivery_execution.md](p3-e6-e4_image_delivery_execution.md) | P3 E6.4 图片、最终交付与 SSE 完成事件的执行清单 |
| [p3-e6-e4_delivery_boundary_decision.md](p3-e6-e4_delivery_boundary_decision.md) | P3 质量通过后如何进入图片交付的流程决策与兼容影响 |
| [p3-e6-e4a_continue_endpoint_execution.md](p3-e6-e4a_continue_endpoint_execution.md) | P3 质量确认后继续图片的 HTTP 接口、恢复和测试执行清单 |
| [p4_execution_plan.md](p4_execution_plan.md) | P4 实时可观测 UI 与运行治理的阶段、边界与回滚方案 |
| [p4-e1_event_replay_execution.md](p4-e1_event_replay_execution.md) 至 [p4-e6_e2e_acceptance_execution.md](p4-e6_e2e_acceptance_execution.md) | P4 各阶段的可执行文档、验收与实施记录 |
| [p5_execution_plan.md](p5_execution_plan.md) | P5 评测集、指标公式与门槛、CI 分层、压测、Docker 演示和 README 的严格交付顺序 |
| [harness_execution_plan.md](harness_execution_plan.md) | H0 基线、统一阶段状态、Harness H1–H5 路线与非目标 |
| [H4 staging result](../harness/H4_STAGING_REPORT.md) | H4 五任务真实模型对比、修复项、限制与默认开关结论 |
| [development_log.md](development_log.md) | 重要开发决策、实施结果与验证记录 |
| [framework-compatibility.md](framework-compatibility.md) | Spring AI / Java / LiteLLM 兼容性与版本冻结证据 |
| [deerflow_reference.md](deerflow_reference.md) | Agent Harness 架构参考材料 |
| [stripe_setup.md](stripe_setup.md) | Stripe 支付配置说明 |
| [vip_features.md](vip_features.md) | VIP 功能说明 |

根目录仅保留 [README.md](../README.md) 作为项目入口，以及 [AGENTS.md](../AGENTS.md) 作为协作规范；新增项目文档请放入本目录并在此索引登记。
