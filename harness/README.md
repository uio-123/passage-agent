# H4 Harness Staging Comparison

This directory records controlled staging evidence for comparing the legacy
content workflow with the P3/Harness quality workflow. Reports are written to
`harness/reports/<run-id>/` and are not release evidence.

The fixed matrix contains five traceable scenarios:

- `NORMAL`: one-section short article.
- `LONG_TASK`: three-section article.
- `REVIEW_REVISION`: two-section article that exercises the quality loop.
- `HUMAN_MODIFY`: human modification maps to a versioned Replan.
- `FAILURE_RECOVERY`: durable node execution retries a failed action and replays without a second side effect.

The curated result and limitations are recorded in
[H4_STAGING_REPORT.md](H4_STAGING_REPORT.md). Timestamped raw reports remain local under
`harness/reports/` and are ignored by Git.

The evidence-based decision for the next scope is recorded in
[H5_DECISION.md](H5_DECISION.md).

The evidence-based decision for the next scope is recorded in
[H5_DECISION.md](H5_DECISION.md).

The curated result and limitations are recorded in
[H4_STAGING_REPORT.md](H4_STAGING_REPORT.md). Timestamped raw reports remain local under
`harness/reports/` and are ignored by Git.

Run with a configured local LiteLLM and Docker:

```powershell
$env:H4_LITELLM_BASE_URL = "http://localhost:4000"
$env:H4_LITELLM_API_KEY = "local-test-key"
$env:H4_LITELLM_MODEL = "qwen3.7-flash"
mvn -q -Pharness-staging -Dtest=HarnessStagingComparisonTest test
```

The first H4 run fixes the title and outline for both variants so that model
randomness in upstream stages does not dominate the comparison. It measures
success, duration, model calls, tokens, tool calls, retries, reviewer scores,
revision rounds, content hash and recovery/idempotency.
