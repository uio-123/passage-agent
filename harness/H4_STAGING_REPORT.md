# H4 Staging Comparison Result

> Status: `STAGING / NON_RELEASE`
> Date: 2026-10-06
> Model: `qwen3.7-flash`
> Evidence run: `harness/reports/h4-staging-2026-10-05T17-43-36.080744700Z/report.json`
> Decision: **DO NOT SWITCH DEFAULT**

## 1. Scope

The run fixed the title and outline for both variants, then compared final content
generation and review:

- `LEGACY`: existing content StateGraph.
- `HARNESS`: P3 section writing, Fact/Style review, bounded revision and Artifact publication.

External image services were explicitly disabled with the `NONE` workspace method.
External research was intentionally disabled so research effects did not contaminate
the content-generation comparison.

## 2. Operational Result

| Metric | Legacy | Harness |
|---|---:|---:|
| Tasks | 5 | 5 |
| Controlled terminal outcomes | 5/5 | 5/5 |
| Average duration | 32.5 s | 107.1 s |
| Model calls | 5 | 24 |
| Input tokens | 1,190 | 7,697 |
| Output tokens | 16,875 | 60,155 |
| Total tokens | 18,065 | 67,852 |
| Tool calls | 0 | 0 |
| Reviewer revision rounds | 0 | 1 |
| Average Fact score | Not measured | 100.0 |
| Average Style score | Not measured | 89.8 |

The `review-revision` Harness scenario intentionally forces one local revision. It then
reached `REJECT_MAX_ROUNDS`, which is a controlled quality-gate outcome, not an execution
failure. Legacy output was not scored by the same reviewer procedure, so the run does not
establish comparative quality superiority.

## 3. Failure Recovery and Human Replan

- Durable node execution recovered from one injected failure.
- The successful result replayed from the durable snapshot without executing the action again.
- Duplicate external side effects: `0`.
- Human `MODIFY` produced `REPLAN` and plan version 2.
- One complete 5 x 2 matrix passed with 120-second model-call timeout protection.

## 4. Findings

The staging run exposed and fixed six real integration defects:

1. Streaming model chunks with `null` content caused `Flux` NPEs.
2. P3 Writer/Reviewer/Revision adapters did not handle model responses wrapped in Markdown code fences.
3. Parallel P3 writer/reviewer calls escaped the metrics scope, losing model call and Token measurements.
4. Empty image-method lists meant “all methods”; an explicit `NONE` method is now required for no-image runs.
5. Legacy graph workers and reactive stream completion could escape the metrics scope; model calls and stream usage are now captured without misattributing concurrent runs.
6. JSON responses with Markdown fences or literal control characters caused parser failures; structured output now tolerates both patterns.

The run also showed:

- Harness average latency was about 3.3 x legacy.
- The Harness workflow made multiple model calls per section.
- Reviewer-driven rework was exercised once and terminated through the bounded quality gate.
- No comparative legacy quality score exists in this run; quality superiority is not demonstrated.

## 5. Decision

Do not switch `article.agent.quality-loop.enabled` to `true`.

Operationally, the Harness path completed 5/5 controlled outcomes and recovery/idempotency
checks passed. However, it used roughly 3.3 x the latency and 3.8 x the reported Tokens,
while no comparative quality benefit has been established. The evidence does not justify
switching the default workflow.
