import { createHash, randomUUID } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { cpus, freemem, platform, release, totalmem } from 'node:os'
import { join } from 'node:path'
import { spawnSync } from 'node:child_process'
import { pathToFileURL } from 'node:url'

export async function runBenchmark(config, baseUrl, options = {}) {
  validateConfig(config)
  const runId = options.runId ?? `draft-load-${new Date().toISOString().replace(/[:.]/g, '-')}`
  const fetchImpl = options.fetchImpl ?? fetch
  const sleepImpl = options.sleepImpl ?? sleep
  const rounds = []
  let ordinal = 0

  for (const concurrency of config.concurrency) {
    for (let repetition = 1; repetition <= config.repetitions; repetition++) {
      if (config.warmupSeconds > 0) {
        await executeWindow(config, baseUrl, fetchImpl, concurrency, config.warmupSeconds * 1000,
          runId, 'warmup', repetition, ordinal)
      }
      const result = await executeWindow(config, baseUrl, fetchImpl, concurrency,
        config.measureSeconds * 1000, runId, 'measure', repetition, ordinal)
      ordinal += result.requests
      rounds.push({ concurrency, repetition, ...result })
      if (config.cooldownSeconds > 0) await sleepImpl(config.cooldownSeconds * 1000)
    }
  }

  const aggregates = config.concurrency.map((concurrency) => aggregateConcurrency(rounds, concurrency))
  return {
    manifest: {
      runId,
      reportStatus: 'DRAFT',
      publicationStatus: 'NON_RELEASE',
      releaseEligible: false,
      releaseBlockers: ['performance baseline not accepted', 'formal evaluation remains locked by human adjudication'],
      scenarioVersion: config.scenarioVersion,
      scenarioSha256: sha256(`${JSON.stringify(config, null, 2)}\n`),
      baseUrl,
      generatedAt: options.generatedAt ?? new Date().toISOString(),
      environment: options.environment ?? environmentFingerprint(),
      disclaimer: 'Deterministic demo-path capacity observation; not real-model throughput and not approved for README or resume claims.',
    },
    rounds,
    summary: {
      reportStatus: 'DRAFT',
      publicationStatus: 'NON_RELEASE',
      aggregates,
      thresholdChecks: thresholdChecks(aggregates),
    },
  }
}

async function executeWindow(config, baseUrl, fetchImpl, concurrency, durationMs,
                             runId, phase, repetition, startingOrdinal) {
  const deadline = Date.now() + durationMs
  const observations = []
  let nextOrdinal = startingOrdinal

  async function worker(workerIndex) {
    while (Date.now() < deadline) {
      const ordinal = nextOrdinal++
      const scenarioType = scenarioForOrdinal(config.mix, ordinal)
      const scenarioId = `${runId}-${phase}-c${concurrency}-r${repetition}-w${workerIndex}-n${ordinal}-${randomUUID()}`
      observations.push(await invoke(config, baseUrl, fetchImpl, scenarioId, scenarioType))
    }
  }
  const started = Date.now()
  await Promise.all(Array.from({ length: concurrency }, (_, index) => worker(index)))
  const elapsedMs = Math.max(1, Date.now() - started)
  return summarizeObservations(observations, elapsedMs)
}

async function invoke(config, baseUrl, fetchImpl, scenarioId, scenarioType) {
  const started = performance.now()
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), config.requestTimeoutMs)
  try {
    const response = await fetchImpl(new URL(config.targetPath, ensureTrailingSlash(baseUrl)), {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ scenarioId, scenarioType }),
      signal: controller.signal,
    })
    const body = await response.json()
    const data = body?.data
    const success = response.ok && body?.code === 0 && data?.runStatus === 'COMPLETED'
      && data?.eventCount >= 1 && data?.artifactCount >= 2
    return {
      success,
      latencyMs: performance.now() - started,
      errorCode: success ? null : sanitizeError(data?.errorCode ?? `HTTP_${response.status}`),
      recoverableFaultInjected: scenarioType === 'RECOVERABLE_FAULT',
      recoverySucceeded: scenarioType === 'RECOVERABLE_FAULT' ? data?.recoverySucceeded === true : null,
      nodeRetryCount: Number.isInteger(data?.nodeRetryCount) ? data.nodeRetryCount : 0,
      duplicateExternalSideEffects: Number.isInteger(data?.duplicateExternalSideEffects) ? data.duplicateExternalSideEffects : 0,
      externalSideEffectAttempts: Number.isInteger(data?.externalSideEffectAttempts) ? data.externalSideEffectAttempts : 0,
    }
  } catch (error) {
    return {
      success: false,
      latencyMs: performance.now() - started,
      errorCode: error?.name === 'AbortError' ? 'REQUEST_TIMEOUT' : 'REQUEST_FAILED',
      recoverableFaultInjected: scenarioType === 'RECOVERABLE_FAULT',
      recoverySucceeded: false,
      nodeRetryCount: 0,
      duplicateExternalSideEffects: 0,
      externalSideEffectAttempts: 0,
    }
  } finally {
    clearTimeout(timer)
  }
}

function summarizeObservations(observations, elapsedMs) {
  const successful = observations.filter((item) => item.success)
  const recoverable = observations.filter((item) => item.recoverableFaultInjected)
  const attempts = sum(observations.map((item) => item.externalSideEffectAttempts))
  const duplicates = sum(observations.map((item) => item.duplicateExternalSideEffects))
  const retries = sum(observations.map((item) => item.nodeRetryCount))
  const recovered = recoverable.filter((item) => item.success && item.recoverySucceeded)
  const errors = {}
  for (const observation of observations.filter((item) => !item.success)) {
    errors[observation.errorCode] = (errors[observation.errorCode] ?? 0) + 1
  }
  return {
    elapsedMs,
    requests: observations.length,
    succeeded: successful.length,
    failed: observations.length - successful.length,
    successRate: ratio(successful.length, observations.length),
    throughputPerMinute: round(successful.length / elapsedMs * 60_000, 4),
    successLatencyP50Ms: nearestRank(successful.map((item) => item.latencyMs), 0.5),
    successLatencyP95Ms: nearestRank(successful.map((item) => item.latencyMs), 0.95),
    recoverySuccessRate: ratio(recoverable.filter((item) => item.recoverySucceeded).length, recoverable.length),
    recoveryLatencyP50Ms: nearestRank(recovered.map((item) => item.latencyMs), 0.5),
    recoveryLatencyP95Ms: nearestRank(recovered.map((item) => item.latencyMs), 0.95),
    nodeRetryCount: retries,
    nodeRetryRate: ratio(retries, observations.length),
    externalSideEffectAttempts: attempts,
    duplicateExternalSideEffects: duplicates,
    duplicateSideEffectRate: ratio(duplicates, attempts),
    errors,
  }
}

export function aggregateConcurrency(rounds, concurrency) {
  const selected = rounds.filter((round) => round.concurrency === concurrency)
  const attempts = sum(selected.map((round) => round.externalSideEffectAttempts))
  const duplicates = sum(selected.map((round) => round.duplicateExternalSideEffects))
  const recoveriesAvailable = selected.map((round) => round.recoverySuccessRate).filter(notNull)
  const retries = sum(selected.map((round) => round.nodeRetryCount))
  const requests = sum(selected.map((round) => round.requests))
  return {
    concurrency,
    repetitions: selected.length,
    requests,
    successRateMedian: nearestRank(selected.map((round) => round.successRate), 0.5),
    throughputPerMinuteMedian: nearestRank(selected.map((round) => round.throughputPerMinute), 0.5),
    successLatencyP50MedianMs: nearestRank(selected.map((round) => round.successLatencyP50Ms).filter(notNull), 0.5),
    successLatencyP95MedianMs: nearestRank(selected.map((round) => round.successLatencyP95Ms).filter(notNull), 0.5),
    recoverySuccessRate: recoveriesAvailable.length ? Math.min(...recoveriesAvailable) : null,
    recoveryLatencyP50MedianMs: nearestRank(selected.map((round) => round.recoveryLatencyP50Ms).filter(notNull), 0.5),
    recoveryLatencyP95MedianMs: nearestRank(selected.map((round) => round.recoveryLatencyP95Ms).filter(notNull), 0.5),
    nodeRetryRate: ratio(retries, requests),
    duplicateSideEffectRate: ratio(duplicates, attempts),
  }
}

function thresholdChecks(aggregates) {
  const c1 = aggregates.find((item) => item.concurrency === 1)
  const c8 = aggregates.find((item) => item.concurrency === 8)
  const checks = {
    allConcurrencySuccessAtLeast99Percent: aggregates.every((item) => item.successRateMedian !== null && item.successRateMedian >= 0.99),
    allRecoverableFaultsRecovered: aggregates.every((item) => item.recoverySuccessRate === null || item.recoverySuccessRate === 1),
    duplicateSideEffectRateZero: aggregates.every((item) => item.duplicateSideEffectRate === null || item.duplicateSideEffectRate === 0),
    concurrency8AtLeastHalfLinearEfficiency: c1 && c8 && c1.throughputPerMinuteMedian > 0
      ? c8.throughputPerMinuteMedian >= c1.throughputPerMinuteMedian * 8 * 0.5 : null,
    acceptedBaselineRegression: null,
  }
  return {
    status: Object.values(checks).some((value) => value === null) ? 'INCOMPLETE_DRAFT'
      : (Object.values(checks).every(Boolean) ? 'DRAFT_PASS' : 'DRAFT_FAIL'),
    checks,
  }
}

function scenarioForOrdinal(mix, ordinal) {
  const entries = Object.entries(mix)
  const total = sum(entries.map(([, weight]) => weight))
  let position = ordinal % total
  for (const [name, weight] of entries) {
    if (position < weight) return name
    position -= weight
  }
  throw new Error('scenario mix is invalid')
}

function validateConfig(config) {
  if (!config.scenarioVersion || config.reportStatus !== 'DRAFT' || config.publicationStatus !== 'NON_RELEASE') {
    throw new Error('scenario must be versioned DRAFT/NON_RELEASE')
  }
  if (!Array.isArray(config.concurrency) || config.concurrency.some((value) => !Number.isInteger(value) || value <= 0)) {
    throw new Error('concurrency must contain positive integers')
  }
  for (const field of ['warmupSeconds', 'measureSeconds', 'cooldownSeconds', 'repetitions', 'requestTimeoutMs']) {
    if (!Number.isFinite(config[field]) || config[field] < 0) throw new Error(`${field} is invalid`)
  }
  if (config.measureSeconds <= 0 || config.repetitions <= 0 || sum(Object.values(config.mix ?? {})) <= 0) {
    throw new Error('measureSeconds, repetitions and scenario mix must be positive')
  }
}

function environmentFingerprint() {
  const git = spawnSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8', shell: false })
  const gitStatus = spawnSync('git', ['status', '--porcelain'], { encoding: 'utf8', shell: false })
  return {
    platform: platform(),
    osRelease: release(),
    cpuModel: cpus()[0]?.model ?? 'unknown',
    cpuCount: cpus().length,
    totalMemoryBytes: totalmem(),
    freeMemoryBytesAtStart: freemem(),
    nodeVersion: process.version,
    gitSha: git.status === 0 ? git.stdout.trim() : 'UNKNOWN',
    gitDirty: gitStatus.status === 0 ? gitStatus.stdout.trim().length > 0 : null,
    hostJavaVersion: commandVersion('java', ['-version']),
    backendJavaVersion: commandVersion('docker', ['exec', 'passage-agent-demo-backend', 'java', '-version']),
    dockerServerVersion: commandVersion('docker', ['version', '--format', '{{.Server.Version}}']),
    backendImageId: commandVersion('docker', ['image', 'inspect', 'ai-passage-backend:latest', '--format', '{{.Id}}']),
  }
}

function commandVersion(command, args) {
  const result = spawnSync(command, args, { encoding: 'utf8', shell: false })
  if (result.status !== 0) return 'UNAVAILABLE'
  return (result.stdout || result.stderr).trim().split(/\r?\n/, 1)[0] || 'UNAVAILABLE'
}

export function writeBenchmarkReport(outputDirectory, report) {
  if (existsSync(outputDirectory)) throw new Error(`performance report directory already exists: ${outputDirectory}`)
  mkdirSync(outputDirectory, { recursive: true })
  writeJson(join(outputDirectory, 'manifest.json'), report.manifest)
  writeJson(join(outputDirectory, 'rounds.json'), report.rounds)
  writeJson(join(outputDirectory, 'summary.json'), report.summary)
}

function ensureTrailingSlash(url) { return url.endsWith('/') ? url : `${url}/` }
function sanitizeError(value) { return String(value).replace(/[^A-Z0-9_:-]/gi, '_').slice(0, 80) }
function nearestRank(values, percentile) { if (!values.length) return null; const sorted = [...values].sort((a, b) => a - b); return round(sorted[Math.ceil(percentile * sorted.length) - 1], 4) }
function ratio(numerator, denominator) { return denominator > 0 ? round(numerator / denominator, 6) : null }
function sum(values) { return values.reduce((total, value) => total + value, 0) }
function round(value, digits) { const factor = 10 ** digits; return Math.round((value + Number.EPSILON) * factor) / factor }
function notNull(value) { return value !== null }
function sha256(value) { return createHash('sha256').update(value, 'utf8').digest('hex') }
function sleep(milliseconds) { return new Promise((resolve) => setTimeout(resolve, milliseconds)) }
function writeJson(path, value) { writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8') }

export async function main(argv) {
  if (argv.length !== 3) {
    console.error('Usage: node performance/run-load.mjs <scenario.json> <base-url> <output-directory>')
    process.exitCode = 2
    return
  }
  const config = JSON.parse(readFileSync(argv[0], 'utf8'))
  writeBenchmarkReport(argv[2], await runBenchmark(config, argv[1]))
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) await main(process.argv.slice(2))
