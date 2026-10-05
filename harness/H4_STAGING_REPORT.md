# H4 Staging Comparison Result

> Status: `STAGING / NON_RELEASE`
> Date: 2026-10-06
> Model: `qwen3.7-flash`
> Evidence run: `harness/reports/h4-staging-2026-10-05T16-01-27.135238300Z/report.json`
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
| Successful tasks | 5/5 | 5/5 |
| Average duration | 37.3 s | 61.3 s |
| Model calls | `UNAVAILABLE` | 16 |
| Input tokens | `UNAVAILABLE` | 3,861 |
| Output tokens | `UNAVAILABLE` | 34,714 |
| Total tokens | `UNAVAILABLE` | 38,575 |
| Tool calls | 0 | 0 |
| Reviewer revision rounds | 0 | 0 |
| Average Fact score | Not measured | 100.0 |
| Average Style score | Not measured | 90.4 |

Legacy model-call/token metrics remain unavailable because the legacy reactive graph
does not currently propagate the active metrics scope through all of its stream
execution threads. It must not be reported as zero.

## 3. Failure Recovery and Human Replan

- Durable node execution recovered from one injected failure.
- The successful result replayed from the durable snapshot without executing the action again.
- Duplicate external side effects: `0`.
- Human `MODIFY` produced `REPLAN` and plan version 2.

## 4. Findings

The staging run exposed and fixed four real integration defects:

1. Streaming model chunks with `null` content caused `Flux` NPEs.
2. P3 Writer/Reviewer/Revision adapters did not handle model responses wrapped in Markdown code fences.
3. Parallel P3 writer/reviewer calls escaped the metrics scope, losing model call and Token measurements.
4. Empty image-method lists meant “all methods”; an explicit `NONE` method is now required for no-image runs.

The run also showed:

- Harness average latency was about 64% higher than legacy.
- The Harness workflow made multiple model calls per section.
- All five Harness tasks were accepted on the first review round, so Reviewer-driven rework was not exercised.
- No comparative legacy quality score exists in this run; quality superiority is not demonstrated.
- A later confirmation run stalled for about 20 minutes in the upstream LiteLLM call before recovering; the successful evidence run remains usable, but model-provider stability is currently insufficient for a release gate.

## 5. Decision

Do not switch `article.agent.quality-loop.enabled` to `true`.

Operationally, the Harness path completed 5/5 tasks and recovery/idempotency checks
passed. However, the evidence does not yet demonstrate a quality benefit large enough
to justify the observed latency and model-call increase. Reviewer rework coverage and
legacy cost metrics must be completed before a default switch or a positive net-benefit
claim.
