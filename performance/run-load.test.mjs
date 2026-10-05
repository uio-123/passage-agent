import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import test from 'node:test'
import { aggregateConcurrency, runBenchmark } from './run-load.mjs'

test('concurrency aggregation uses medians and preserves recovery/idempotency signals', () => {
  const aggregate = aggregateConcurrency([
    { concurrency: 4, requests: 10, successRate: 1, throughputPerMinute: 100, successLatencyP50Ms: 10,
      successLatencyP95Ms: 20, recoverySuccessRate: 1, recoveryLatencyP50Ms: 12, recoveryLatencyP95Ms: 18,
      nodeRetryCount: 1, externalSideEffectAttempts: 10, duplicateExternalSideEffects: 0 },
    { concurrency: 4, requests: 10, successRate: 0.9, throughputPerMinute: 80, successLatencyP50Ms: 12,
      successLatencyP95Ms: 25, recoverySuccessRate: 1, recoveryLatencyP50Ms: 14, recoveryLatencyP95Ms: 21,
      nodeRetryCount: 1, externalSideEffectAttempts: 10, duplicateExternalSideEffects: 1 },
    { concurrency: 4, requests: 10, successRate: 1, throughputPerMinute: 90, successLatencyP50Ms: 11,
      successLatencyP95Ms: 22, recoverySuccessRate: 1, recoveryLatencyP50Ms: 13, recoveryLatencyP95Ms: 20,
      nodeRetryCount: 1, externalSideEffectAttempts: 10, duplicateExternalSideEffects: 0 },
  ], 4)
  assert.equal(aggregate.throughputPerMinuteMedian, 90)
  assert.equal(aggregate.successLatencyP95MedianMs, 22)
  assert.equal(aggregate.recoveryLatencyP95MedianMs, 20)
  assert.equal(aggregate.nodeRetryRate, 0.1)
  assert.equal(aggregate.duplicateSideEffectRate, 0.033333)
})

test('short benchmark hits the HTTP scenario and remains DRAFT/NON_RELEASE', async () => {
  let requestCount = 0
  const server = createServer((request, response) => {
    request.resume()
    request.on('end', () => {
      requestCount++
      response.writeHead(200, { 'content-type': 'application/json' })
      response.end(JSON.stringify({ code: 0, data: { runStatus: 'COMPLETED', eventCount: 4, artifactCount: 3,
        recoverySucceeded: true, nodeRetryCount: 1, externalSideEffectAttempts: 1, duplicateExternalSideEffects: 0 } }))
    })
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  try {
    const address = server.address()
    const config = {
      scenarioVersion: 'test-v1', reportStatus: 'DRAFT', publicationStatus: 'NON_RELEASE',
      targetPath: '/api/demo/scenarios/run', concurrency: [1, 2], warmupSeconds: 0,
      measureSeconds: 0.05, cooldownSeconds: 0, repetitions: 1, requestTimeoutMs: 1000,
      mix: { NORMAL_RESEARCH: 6, NO_RESEARCH: 2, REVIEW_REVISION: 1, RECOVERABLE_FAULT: 1 },
    }
    const report = await runBenchmark(config, `http://127.0.0.1:${address.port}`, {
      runId: 'test-run', generatedAt: '2026-08-31T04:00:00+08:00', environment: { kind: 'test' },
    })
    assert.equal(report.manifest.reportStatus, 'DRAFT')
    assert.equal(report.manifest.publicationStatus, 'NON_RELEASE')
    assert.equal(report.manifest.releaseEligible, false)
    assert.equal(report.rounds.length, 2)
    assert.ok(requestCount > 0)
    assert.ok(report.summary.aggregates.every((item) => item.successRateMedian === 1))
  } finally {
    await new Promise((resolve) => server.close(resolve))
  }
})
