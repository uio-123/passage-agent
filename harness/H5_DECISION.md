# H5 Evidence-Based Scope Decision

> Status: `DONE`
> Date: 2026-10-06
> Basis: [H4 staging result](H4_STAGING_REPORT.md)
> Decision: **DEFER MEMORY, COMPRESSION, DEFAULT SWITCH, FORMAL P5 AND SANDBOX**

## 1. Decision Matrix

| Scope | Decision | Reason |
|---|---|---|
| Memory Manager / Retrieval | `DEFER` | No evidence that repeated-task memory improves the five-task workflow; adds privacy, tenancy and retrieval-quality risk |
| Context Compression | `DEFER` | Context size was not the observed bottleneck; H4 latency came from additional model calls and review/revision overhead |
| P3 default switch | `KEEP false` | Harness passed all checks, but used about 3.3 x legacy latency and 3.8 x reported Tokens without a demonstrated quality benefit |
| Formal P5 30 x 3 evaluation | `DEFER` | No comparative quality benefit or latency justification is established; formal evaluation would be premature |
| Docker Sandbox | `DEFER` | No arbitrary code or untrusted filesystem execution requirement exists in the current product scope |

No production default behavior changes are approved by this decision.

## 2. H4 Evidence That Blocks Expansion

- Harness and legacy both completed 5/5 tasks.
- Harness average duration was about 107.1 seconds versus 32.5 seconds for legacy.
- Harness used 24 model calls and 67,852 reported Tokens; legacy used 5 calls and 18,065 Tokens.
- Reviewer-driven rework was exercised once and ended in a controlled `REJECT_MAX_ROUNDS`.
- Recovery and duplicate-side-effect checks passed.
- The final 5 x 2 matrix completed with 120-second model-call timeout protection.

## 3. Re-entry Gates

Memory, compression, formal P5 and the default switch may be reconsidered only after all of the following:

1. **Rework coverage**: satisfied on the final run; one scenario entered revision and completed through the bounded quality gate.
2. **Comparable quality**: legacy and Harness outputs are scored by the same frozen reviewer procedure; Harness improves the agreed quality metric by at least 3 absolute points or materially reduces human edits.
3. **Cost visibility**: satisfied for model calls and provider-reported Tokens.
4. **Provider stability**: one complete 5 x 2 matrix passed under the 120-second call timeout.
5. **Latency budget**: Harness end-to-end P95 is no more than 1.5 x legacy for equivalent quality, or the measured quality gain justifies the observed overhead.
6. **Repeatability**: one full 5 x 2 matrix must complete without duplicate side effects or state corruption. Three repeated matrices are required only when claiming production-style stability or reliability.

Until these gates pass, adding Memory or compression would increase complexity without evidence that it addresses the actual bottleneck.

## 4. Immediate Backlog

1. Score legacy content with the same frozen reviewer procedure used for Harness output.
2. Reduce Harness latency and model-call overhead before considering a default switch.
3. Re-run one full 5 x 2 matrix before the interview if code changes affect content generation or review.
4. Keep three repeated matrices and formal P5 optional; they are required only for stability or quantified-benefit claims.

## 5. Final Scope Decision

The Harness architecture is retained as an opt-in staging capability. The original H4
instrumentation, rework and timeout gaps are closed. The remaining blocker is the measured
latency/Token overhead without a demonstrated comparative quality benefit.

For the resume-project target, the complete policy is defined in
[`docs/resume_validation_plan.md`](../docs/resume_validation_plan.md).
