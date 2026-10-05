import assert from 'node:assert/strict'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { buildReport, nearestRank, scoreSample, writeReport } from './score-run.mjs'

const datasetText = readFileSync(new URL('../datasets/p5-v1/samples.json', import.meta.url), 'utf8')
const dataset = JSON.parse(datasetText)
const manifest = JSON.parse(readFileSync(new URL('../datasets/p5-v1/dataset-manifest.json', import.meta.url), 'utf8'))
const pricing = JSON.parse(readFileSync(new URL('../pricing/fixture-model-v1.json', import.meta.url), 'utf8'))
const input = JSON.parse(readFileSync(new URL('../fixtures/draft-scorer-input.json', import.meta.url), 'utf8'))

test('nearest-rank uses ceil percentile position', () => {
  assert.equal(nearestRank([5, 1, 4, 2, 3], 0.5), 3)
  assert.equal(nearestRank([5, 1, 4, 2, 3], 0.95), 5)
  assert.equal(nearestRank([], 0.95), null)
})

test('sample scorer applies weighted facts, citations, quality and exact fixture cost', () => {
  const row = scoreSample(dataset.samples[0], input.samples[0], pricing)
  assert.equal(row.factSupportRate, 0.75)
  assert.equal(row.citationValidityRate, 0.666667)
  assert.equal(row.citationCoverageRate, 0.666667)
  assert.equal(row.structureCompleteness, 66.6667)
  assert.equal(row.styleConsistency, 50)
  assert.equal(row.readability, 50)
  assert.equal(row.qualityComplete, true)
  assert.equal(row.usageStatus, 'COMPLETE')
  assert.equal(row.inputTokens, 1000)
  assert.equal(row.outputTokens, 500)
  assert.equal(row.estimatedCost, 0.00125)
})

test('missing usage is PARTIAL and never becomes zero cost', () => {
  const row = scoreSample(dataset.samples[0], input.samples[1], pricing)
  assert.equal(row.usageStatus, 'PARTIAL')
  assert.equal(row.inputTokens, null)
  assert.equal(row.outputTokens, null)
  assert.equal(row.estimatedCost, null)
})

test('draft scorer output remains locked while dataset is not adjudicated', () => {
  const report = buildReport(dataset, manifest, pricing, input, {
    datasetText,
    generatedAt: '2026-08-31T03:00:00+08:00',
  })
  assert.equal(report.manifest.reportStatus, 'DRAFT')
  assert.equal(report.manifest.publicationStatus, 'NON_RELEASE')
  assert.equal(report.manifest.releaseGateStatus, 'LOCKED_NON_RELEASE')
  assert.equal(report.manifest.releaseEligible, false)
  assert.ok(report.manifest.releaseBlockers.includes('dataset manifest releaseReady=false'))
  assert.equal(report.summary.variants.length, 3)
  assert.equal(report.summary.comparisons.length, 3)
  assert.equal(report.summary.comparisons[1].estimatedCostAbsoluteDelta, null)
  assert.equal(report.summary.contextStrategies[0].status, 'NOT_IMPLEMENTED')
})

test('release mode fails closed instead of relabeling draft data', () => {
  assert.throws(() => buildReport(dataset, manifest, pricing, input, { datasetText, release: true }), /release report is locked/)
})

test('report writer creates parents but never overwrites a run directory', () => {
  const root = mkdtempSync(join(tmpdir(), 'passage-eval-'))
  try {
    const output = join(root, 'reports', 'run-1')
    const report = buildReport(dataset, manifest, pricing, input, { datasetText })
    writeReport(output, report)
    assert.equal(JSON.parse(readFileSync(join(output, 'manifest.json'), 'utf8')).reportStatus, 'DRAFT')
    assert.throws(() => writeReport(output, report), /already exists/)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})
