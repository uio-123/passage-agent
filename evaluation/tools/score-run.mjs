import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'
import { join } from 'node:path'
import { normalizedSha256 } from './review-workflow.mjs'

const VARIANTS = ['legacy', 'multi_agent_no_review', 'multi_agent_full']

export function scoreSample(datasetSample, measurement, pricing) {
  if (datasetSample.id !== measurement.sampleId) throw new Error('sample id mismatch')
  if (!VARIANTS.includes(measurement.variant)) throw new Error(`unknown variant: ${measurement.variant}`)

  const fsr = weightedFactSupport(measurement.claims ?? [])
  const citations = citationScores(datasetSample, measurement.citations ?? [])
  const structure = structureScore(datasetSample, measurement)
  const style = rubricScore(measurement.styleScores)
  const readability = rubricScore(measurement.readabilityScores)
  const quality = qualityScores(datasetSample.requiresResearch, { fsr, citations, structure, style, readability })
  const timing = timingScores(measurement)
  const usage = usageScores(measurement.modelCalls ?? [], pricing)
  const revisionGain = pairedMeanDifference(measurement.preRevisionScores ?? [], measurement.postRevisionScores ?? [])
  const unchangedRatio = ratio(measurement.byteIdenticalUnmarkedSections, measurement.unmarkedSections)

  return {
    sampleId: measurement.sampleId,
    domain: datasetSample.domain,
    variant: measurement.variant,
    terminalStatus: measurement.terminalStatus,
    failureCode: measurement.failureCode ?? null,
    factSupportRate: fsr,
    citationValidityRate: citations.validity,
    citationCoverageRate: citations.coverage,
    structureCompleteness: structure,
    styleConsistency: style,
    readability,
    provisionalQualityScore: quality.provisional,
    qualityScore: quality.complete ? quality.provisional : null,
    qualityComponentCoverage: quality.coverage,
    qualityComplete: quality.complete,
    criticalFailures: [...(measurement.criticalFailures ?? [])],
    endToEndMs: timing.endToEndMs,
    firstTokenMs: timing.firstTokenMs,
    usageStatus: usage.status,
    inputTokens: usage.inputTokens,
    outputTokens: usage.outputTokens,
    estimatedCost: usage.estimatedCost,
    currency: pricing.currency,
    recoverableFaultInjected: Boolean(measurement.recoverableFaultInjected),
    recoverySucceeded: measurement.recoverySucceeded ?? null,
    externalSideEffectAttempts: integer(measurement.externalSideEffectAttempts, 'externalSideEffectAttempts'),
    duplicateExternalSideEffects: integer(measurement.duplicateExternalSideEffects, 'duplicateExternalSideEffects'),
    revisionGain,
    unmarkedSectionPreservationRate: unchangedRatio,
    unmarkedSections: integer(measurement.unmarkedSections, 'unmarkedSections'),
    byteIdenticalUnmarkedSections: integer(measurement.byteIdenticalUnmarkedSections, 'byteIdenticalUnmarkedSections'),
  }
}

export function aggregateVariant(scored, variant) {
  const rows = scored.filter((row) => row.variant === variant)
  const succeeded = rows.filter((row) => row.terminalStatus === 'SUCCEEDED')
  const researchRows = rows.filter((row) => row.citationValidityRate !== null)
  const qualityRows = rows.filter((row) => row.qualityScore !== null)
  const costsComplete = succeeded.length > 0 && succeeded.every((row) => row.usageStatus === 'COMPLETE')
  const injected = rows.filter((row) => row.recoverableFaultInjected)
  const sideEffects = sum(rows.map((row) => row.externalSideEffectAttempts))
  const duplicates = sum(rows.map((row) => row.duplicateExternalSideEffects))
  const domainFsr = Object.fromEntries(['TECHNOLOGY', 'EDUCATION', 'EMOTION'].map((domain) => {
    const values = rows.filter((row) => row.domain === domain).map((row) => row.factSupportRate).filter(notNull)
    return [domain, average(values)]
  }))

  return {
    variant,
    samples: rows.length,
    succeeded: succeeded.length,
    failed: rows.length - succeeded.length,
    successRate: ratio(succeeded.length, rows.length),
    macroFactSupportRate: average(rows.map((row) => row.factSupportRate).filter(notNull)),
    factSupportByDomain: domainFsr,
    macroCitationValidityRate: average(researchRows.map((row) => row.citationValidityRate).filter(notNull)),
    macroCitationCoverageRate: average(researchRows.map((row) => row.citationCoverageRate).filter(notNull)),
    qualityMedian: nearestRank(qualityRows.map((row) => row.qualityScore), 0.5),
    qualityAtLeast75: qualityRows.filter((row) => row.qualityScore >= 75).length,
    qualityCompleteSamples: qualityRows.length,
    criticalFailureCount: sum(rows.map((row) => row.criticalFailures.length)),
    endToEndP50Ms: nearestRank(succeeded.map((row) => row.endToEndMs).filter(notNull), 0.5),
    endToEndP95Ms: nearestRank(succeeded.map((row) => row.endToEndMs).filter(notNull), 0.95),
    firstTokenP50Ms: nearestRank(succeeded.map((row) => row.firstTokenMs).filter(notNull), 0.5),
    firstTokenP95Ms: nearestRank(succeeded.map((row) => row.firstTokenMs).filter(notNull), 0.95),
    usageCompleteSamples: succeeded.filter((row) => row.usageStatus === 'COMPLETE').length,
    usageCoverageRate: ratio(succeeded.filter((row) => row.usageStatus === 'COMPLETE').length, succeeded.length),
    totalInputTokens: costsComplete ? sum(succeeded.map((row) => row.inputTokens)) : null,
    totalOutputTokens: costsComplete ? sum(succeeded.map((row) => row.outputTokens)) : null,
    totalEstimatedCost: costsComplete ? round(sum(succeeded.map((row) => row.estimatedCost)), 8) : null,
    recoverySuccessRate: ratio(injected.filter((row) => row.recoverySucceeded).length, injected.length),
    duplicateSideEffectRate: ratio(duplicates, sideEffects),
    averageRevisionGain: average(rows.map((row) => row.revisionGain).filter(notNull)),
    unmarkedSectionPreservationRate: weightedRatio(rows, 'byteIdenticalUnmarkedSections', 'unmarkedSections'),
  }
}

export function buildReport(dataset, manifest, pricing, input, options = {}) {
  const datasetText = options.datasetText ?? `${JSON.stringify(dataset, null, 2)}\n`
  const datasetSha = normalizedSha256(datasetText)
  if (manifest.sampleFileSha256 !== datasetSha) throw new Error('dataset manifest SHA-256 mismatch')
  if (input.datasetVersion !== dataset.datasetVersion || input.datasetSha256 !== datasetSha) {
    throw new Error('run input does not target the frozen dataset')
  }
  const byId = new Map(dataset.samples.map((sample) => [sample.id, sample]))
  const scored = input.samples.map((measurement) => {
    const sample = byId.get(measurement.sampleId)
    if (!sample) throw new Error(`unknown dataset sample: ${measurement.sampleId}`)
    return scoreSample(sample, measurement, pricing)
  })
  ensureUniqueMeasurements(scored)

  const releaseRequested = options.release === true
  const releaseBlockers = releaseBlockersFor(dataset, manifest, input, scored)
  if (releaseRequested && releaseBlockers.length) {
    throw new Error(`release report is locked: ${releaseBlockers.join('; ')}`)
  }
  const aggregates = VARIANTS.map((variant) => aggregateVariant(scored, variant))
  const full = aggregates.find((item) => item.variant === 'multi_agent_full')
  const draftChecks = thresholdChecks(full)
  return {
    manifest: {
      runId: input.runId,
      protocolVersion: input.protocolVersion,
      reportStatus: releaseRequested ? 'RELEASE' : 'DRAFT',
      publicationStatus: releaseRequested ? 'RELEASE_APPROVED' : 'NON_RELEASE',
      releaseGateStatus: releaseRequested ? 'EVALUATED' : 'LOCKED_NON_RELEASE',
      releaseEligible: releaseBlockers.length === 0,
      releaseBlockers,
      sourceMode: input.sourceMode,
      datasetVersion: dataset.datasetVersion,
      datasetSha256: datasetSha,
      pricingVersion: pricing.pricingVersion,
      pricingSource: pricing.source,
      currency: pricing.currency,
      gitSha: input.gitSha,
      modelConfig: input.modelConfig,
      contextStrategy: input.contextStrategy,
      generatedAt: options.generatedAt ?? new Date().toISOString(),
      disclaimer: releaseRequested ? null : 'NON_RELEASE scorer output; not a formal evaluation result and not approved for README or resume claims.',
    },
    samples: scored,
    summary: {
      reportStatus: releaseRequested ? 'RELEASE' : 'DRAFT',
      publicationStatus: releaseRequested ? 'RELEASE_APPROVED' : 'NON_RELEASE',
      variants: aggregates,
      comparisons: [
        compareVariants(aggregates, 'legacy', 'multi_agent_no_review'),
        compareVariants(aggregates, 'multi_agent_no_review', 'multi_agent_full'),
        compareVariants(aggregates, 'legacy', 'multi_agent_full'),
      ],
      draftThresholdChecks: draftChecks,
      contextStrategies: [{ strategy: input.contextStrategy, status: input.contextStrategy === 'NOT_IMPLEMENTED' ? 'NOT_IMPLEMENTED' : 'MEASURED' }],
    },
  }
}

export function writeReport(outputDirectory, report) {
  if (existsSync(outputDirectory)) throw new Error(`report directory already exists: ${outputDirectory}`)
  mkdirSync(outputDirectory, { recursive: true })
  writeJson(join(outputDirectory, 'manifest.json'), report.manifest)
  writeJson(join(outputDirectory, 'samples.json'), report.samples)
  writeJson(join(outputDirectory, 'summary.json'), report.summary)
}

function weightedFactSupport(claims) {
  if (!claims.length) return null
  let supported = 0
  let eligible = 0
  for (const claim of claims) {
    const weight = claim.weight
    if (![1, 2].includes(weight)) throw new Error('claim weight must be 1 or 2')
    if (!['SUPPORTED', 'UNSUPPORTED', 'CONTRADICTED'].includes(claim.classification)) throw new Error('invalid claim classification')
    eligible += weight
    if (claim.classification === 'SUPPORTED') supported += weight
  }
  return ratio(supported, eligible)
}

function citationScores(sample, citations) {
  if (!sample.requiresResearch) return { validity: null, coverage: null }
  const validity = ratio(citations.filter((item) => item.valid === true).length, citations.length, citations.length === 0 ? 0 : null)
  const required = new Set(sample.requiredCitationFactIds)
  const covered = new Set(citations.filter((item) => item.valid === true && required.has(item.factId)).map((item) => item.factId))
  return { validity, coverage: ratio(covered.size, required.size, required.size > 0 ? 0 : null) }
}

function structureScore(sample, measurement) {
  const required = new Set(sample.outlineRequirements.requiredSections)
  const satisfied = new Set((measurement.satisfiedRequiredSections ?? []).filter((item) => required.has(item)))
  const base = ratio(satisfied.size, required.size) * 100
  return Math.max(0, round(base - (measurement.forbiddenSectionsPresent ?? []).length * 10, 4))
}

function rubricScore(values) {
  if (values === null || values === undefined) return null
  if (!Array.isArray(values) || values.length !== 5 || values.some((value) => ![0, 1, 2].includes(value))) {
    throw new Error('rubric scores must contain five values in 0..2')
  }
  return round(sum(values) / 10 * 100, 4)
}

function qualityScores(research, components) {
  const weights = research
    ? { fsr: 0.30, citation: 0.20, structure: 0.20, style: 0.15, readability: 0.15 }
    : { fsr: 0.40, structure: 0.25, style: 0.175, readability: 0.175 }
  const values = {
    fsr: components.fsr === null ? null : components.fsr * 100,
    citation: research ? (0.6 * components.citations.validity + 0.4 * components.citations.coverage) * 100 : null,
    structure: components.structure,
    style: components.style,
    readability: components.readability,
  }
  let weighted = 0
  let applicableWeight = 0
  for (const [key, weight] of Object.entries(weights)) {
    if (values[key] !== null) {
      weighted += values[key] * weight
      applicableWeight += weight
    }
  }
  const missingHuman = components.style === null || components.readability === null
  const missingRequiredCitation = research && (components.citations.validity === null || components.citations.coverage === null)
  return {
    provisional: applicableWeight === 0 ? null : round(weighted / applicableWeight, 4),
    coverage: round(applicableWeight / sum(Object.values(weights)), 4),
    complete: !missingHuman && !missingRequiredCitation && applicableWeight > 0,
  }
}

function timingScores(measurement) {
  const started = Date.parse(measurement.startedAt)
  const completed = Date.parse(measurement.completedAt)
  if (Number.isNaN(started) || Number.isNaN(completed) || completed < started) throw new Error('invalid sample timing')
  const firstTokenMs = measurement.firstTokenMs ?? null
  if (firstTokenMs !== null && (!Number.isFinite(firstTokenMs) || firstTokenMs < 0)) throw new Error('invalid firstTokenMs')
  return { endToEndMs: completed - started, firstTokenMs }
}

function usageScores(calls, pricing) {
  if (!calls.length) return { status: 'UNAVAILABLE', inputTokens: null, outputTokens: null, estimatedCost: null }
  let inputTokens = 0
  let outputTokens = 0
  let cost = 0
  for (const call of calls) {
    if (call.inputTokens === null || call.outputTokens === null) {
      return { status: 'PARTIAL', inputTokens: null, outputTokens: null, estimatedCost: null }
    }
    const price = pricing.models[call.model]
    if (!price) return { status: 'PRICE_UNAVAILABLE', inputTokens: null, outputTokens: null, estimatedCost: null }
    inputTokens += integer(call.inputTokens, 'inputTokens')
    outputTokens += integer(call.outputTokens, 'outputTokens')
    cost += call.inputTokens / 1_000_000 * price.inputPerMillion + call.outputTokens / 1_000_000 * price.outputPerMillion
  }
  return { status: 'COMPLETE', inputTokens, outputTokens, estimatedCost: round(cost, 8) }
}

function releaseBlockersFor(dataset, manifest, input, scored) {
  const blockers = []
  if (!manifest.releaseReady) blockers.push('dataset manifest releaseReady=false')
  if (dataset.samples.some((sample) => sample.annotationStatus !== 'ADJUDICATED')) blockers.push('dataset contains non-adjudicated samples')
  if (input.sourceMode === 'SCORER_SELF_TEST') blockers.push('sourceMode is SCORER_SELF_TEST')
  if (scored.length !== dataset.samples.length * VARIANTS.length) blockers.push('three-variant 30-sample matrix is incomplete')
  const keys = new Set(scored.map((row) => `${row.sampleId}|${row.variant}`))
  let missingMatrixCells = 0
  for (const sample of dataset.samples) for (const variant of VARIANTS) {
    if (!keys.has(`${sample.id}|${variant}`)) missingMatrixCells++
  }
  if (missingMatrixCells) blockers.push(`three-variant matrix has ${missingMatrixCells} missing cells`)
  const reviews = input.humanProductReviews ?? []
  if (reviews.length < 9) blockers.push('fewer than 9 human product reviews')
  for (const domain of ['TECHNOLOGY', 'EDUCATION', 'EMOTION']) {
    if (reviews.filter((review) => review.domain === domain).length < 3) blockers.push(`fewer than 3 human reviews for ${domain}`)
  }
  if (scored.some((row) => !row.qualityComplete)) blockers.push('quality components are incomplete')
  return [...new Set(blockers)]
}

function compareVariants(aggregates, baselineName, candidateName) {
  const baseline = aggregates.find((item) => item.variant === baselineName)
  const candidate = aggregates.find((item) => item.variant === candidateName)
  const qualityDelta = difference(candidate.qualityMedian, baseline.qualityMedian)
  const costDelta = difference(candidate.totalEstimatedCost, baseline.totalEstimatedCost)
  return {
    baseline: baselineName,
    candidate: candidateName,
    qualityMedianAbsoluteDelta: qualityDelta,
    qualityMedianRelativeDelta: relativeDifference(candidate.qualityMedian, baseline.qualityMedian),
    macroFactSupportAbsoluteDelta: difference(candidate.macroFactSupportRate, baseline.macroFactSupportRate),
    macroCitationValidityAbsoluteDelta: difference(candidate.macroCitationValidityRate, baseline.macroCitationValidityRate),
    endToEndP95AbsoluteDeltaMs: difference(candidate.endToEndP95Ms, baseline.endToEndP95Ms),
    endToEndP95RelativeDelta: relativeDifference(candidate.endToEndP95Ms, baseline.endToEndP95Ms),
    inputTokenRelativeDelta: relativeDifference(candidate.totalInputTokens, baseline.totalInputTokens),
    outputTokenRelativeDelta: relativeDifference(candidate.totalOutputTokens, baseline.totalOutputTokens),
    estimatedCostAbsoluteDelta: costDelta,
    marginalCostPerQualityPoint: qualityDelta !== null && qualityDelta > 0 && costDelta !== null ? round(costDelta / qualityDelta, 8) : null,
  }
}

function thresholdChecks(full) {
  if (!full || full.samples === 0) return { status: 'UNAVAILABLE', reasons: ['multi_agent_full has no samples'] }
  const checks = {
    successRateAtLeast29Of30: full.samples === 30 ? full.succeeded >= 29 : null,
    noCriticalFailures: full.criticalFailureCount === 0,
    macroFsrAtLeast090: full.macroFactSupportRate === null ? null : full.macroFactSupportRate >= 0.90,
    macroCvrAtLeast095: full.macroCitationValidityRate === null ? null : full.macroCitationValidityRate >= 0.95,
    macroCcrAtLeast090: full.macroCitationCoverageRate === null ? null : full.macroCitationCoverageRate >= 0.90,
    qualityMedianAtLeast80: full.qualityMedian === null ? null : full.qualityMedian >= 80,
    atLeast24QualityScoresAt75: full.samples === 30 && full.qualityCompleteSamples === 30 ? full.qualityAtLeast75 >= 24 : null,
    recoveryRate100Percent: full.recoverySuccessRate === null ? null : full.recoverySuccessRate === 1,
    duplicateSideEffectRateZero: full.duplicateSideEffectRate === null ? null : full.duplicateSideEffectRate === 0,
  }
  return { status: Object.values(checks).some((value) => value === null) ? 'INCOMPLETE_DRAFT' : (Object.values(checks).every(Boolean) ? 'DRAFT_PASS' : 'DRAFT_FAIL'), checks }
}

function ensureUniqueMeasurements(rows) {
  const keys = new Set()
  for (const row of rows) {
    const key = `${row.sampleId}|${row.variant}`
    if (keys.has(key)) throw new Error(`duplicate measurement: ${key}`)
    keys.add(key)
  }
}

function pairedMeanDifference(before, after) {
  if (!before.length && !after.length) return null
  if (before.length !== after.length || before.some((value) => !Number.isFinite(value)) || after.some((value) => !Number.isFinite(value))) {
    throw new Error('revision score pairs are invalid')
  }
  return round(average(after.map((value, index) => value - before[index])), 4)
}

function weightedRatio(rows, numeratorField, denominatorField) {
  const numerator = sum(rows.map((row) => row[numeratorField] ?? 0))
  const denominator = sum(rows.map((row) => row[denominatorField] ?? 0))
  return ratio(numerator, denominator)
}

export function nearestRank(values, percentile) {
  if (!values.length) return null
  const sorted = [...values].sort((a, b) => a - b)
  return sorted[Math.ceil(percentile * sorted.length) - 1]
}

function average(values) { return values.length ? round(sum(values) / values.length, 6) : null }
function difference(candidate, baseline) { return candidate === null || baseline === null ? null : round(candidate - baseline, 6) }
function relativeDifference(candidate, baseline) { return candidate === null || baseline === null || baseline === 0 ? null : round((candidate - baseline) / baseline, 6) }
function sum(values) { return values.reduce((total, value) => total + value, 0) }
function notNull(value) { return value !== null }
function ratio(numerator, denominator, zeroDenominator = null) { return denominator > 0 ? round(numerator / denominator, 6) : zeroDenominator }
function round(value, digits) { const factor = 10 ** digits; return Math.round((value + Number.EPSILON) * factor) / factor }
function integer(value, name) { if (!Number.isInteger(value) || value < 0) throw new Error(`${name} must be a non-negative integer`); return value }
function writeJson(path, value) { writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8') }

export function main(argv) {
  const releaseIndex = argv.indexOf('--release')
  const release = releaseIndex >= 0
  const positional = argv.filter((_, index) => index !== releaseIndex)
  if (positional.length !== 5) {
    console.error('Usage: node score-run.mjs <samples.json> <manifest.json> <pricing.json> <run-input.json> <output-directory> [--release]')
    process.exitCode = 2
    return
  }
  const [datasetPath, manifestPath, pricingPath, inputPath, outputDirectory] = positional
  const datasetText = readFileSync(datasetPath, 'utf8')
  const report = buildReport(JSON.parse(datasetText), JSON.parse(readFileSync(manifestPath, 'utf8')),
    JSON.parse(readFileSync(pricingPath, 'utf8')), JSON.parse(readFileSync(inputPath, 'utf8')),
    { release, datasetText })
  writeReport(outputDirectory, report)
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) main(process.argv.slice(2))
