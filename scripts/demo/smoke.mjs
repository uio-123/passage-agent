const baseUrl = (process.argv[2] ?? 'http://localhost:18123').replace(/\/$/, '')
const timeoutMs = Number(process.argv[3] ?? 300_000)

async function waitForHealth() {
  const deadline = Date.now() + timeoutMs
  let lastError = 'not attempted'
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`${baseUrl}/api/health/`)
      const body = await response.json()
      if (response.ok && body?.code === 0 && body?.data === 'ok') return
      lastError = `HTTP ${response.status}`
    } catch (error) {
      lastError = error.message
    }
    await new Promise((resolve) => setTimeout(resolve, 2_000))
  }
  throw new Error(`backend did not become healthy within ${timeoutMs}ms: ${lastError}`)
}

async function runScenario() {
  const response = await fetch(`${baseUrl}/api/demo/scenarios/run`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ scenarioId: 'p5-docker-smoke-v1', scenarioType: 'RECOVERABLE_FAULT' }),
  })
  const body = await response.json()
  if (!response.ok || body?.code !== 0) throw new Error(`demo request failed: HTTP ${response.status}`)
  const data = body.data
  if (data.runStatus !== 'COMPLETED' || data.versionCount !== 1 || data.artifactCount !== 3
      || data.eventCount < 7 || data.recoverySucceeded !== true || data.duplicateExternalSideEffects !== 0) {
    throw new Error(`demo assertions failed: ${JSON.stringify(data)}`)
  }
  return data
}

await waitForHealth()
const first = await runScenario()
const second = await runScenario()
if (first.runId !== second.runId || first.eventCount !== second.eventCount || second.reused !== true) {
  throw new Error('demo idempotency assertion failed')
}
process.stdout.write(`${JSON.stringify({ status: 'PASS', runId: first.runId, eventCount: first.eventCount,
  artifactCount: first.artifactCount, idempotentReplay: second.reused }, null, 2)}\n`)
