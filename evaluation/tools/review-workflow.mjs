import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

const DECISION_FIELDS = ['statementMatchesReplay', 'weightAppropriate']

export function normalizedSha256(text) {
  return createHash('sha256').update(text.replace(/\r\n/g, '\n'), 'utf8').digest('hex')
}

export function loadJson(path) {
  return JSON.parse(readFileSync(path, 'utf8'))
}

export function createReviewTemplate(dataset, datasetSha256, reviewerId) {
  requireReviewerId(reviewerId)
  return {
    datasetVersion: dataset.datasetVersion,
    datasetSha256,
    reviewerId,
    role: 'REVIEWER',
    reviewedAt: null,
    samples: dataset.samples.map((sample) => ({
      sampleId: sample.id,
      facts: sample.referenceFacts.map((fact) => ({
        factId: fact.id,
        statementMatchesReplay: null,
        weightAppropriate: null,
        sourceSupport: fact.allowedSourceIds.map((sourceId) => ({ sourceId, supports: null })),
        notes: '',
      })),
      sources: sample.replaySources.map((source) => ({
        sourceId: source.id,
        excerptSafeAndSufficient: null,
        notes: '',
      })),
      forbiddenClaims: sample.forbiddenClaims.map((claim) => ({
        claim,
        appropriate: null,
        notes: '',
      })),
      sampleDecision: null,
      notes: '',
    })),
  }
}

export function validateCompletedReview(dataset, datasetSha256, review) {
  const errors = []
  if (review.datasetVersion !== dataset.datasetVersion) errors.push('datasetVersion mismatch')
  if (review.datasetSha256 !== datasetSha256) errors.push('datasetSha256 mismatch')
  try { requireReviewerId(review.reviewerId) } catch (error) { errors.push(error.message) }
  if (review.role !== 'REVIEWER') errors.push('role must be REVIEWER')
  if (!isIsoDate(review.reviewedAt)) errors.push('reviewedAt must be an ISO date-time')
  if (!Array.isArray(review.samples) || review.samples.length !== dataset.samples.length) {
    errors.push(`samples must contain ${dataset.samples.length} entries`)
    return errors
  }

  const reviewsById = uniqueBy(review.samples, 'sampleId', errors, 'review sample')
  for (const sample of dataset.samples) {
    const reviewed = reviewsById.get(sample.id)
    if (!reviewed) {
      errors.push(`${sample.id}: missing review`)
      continue
    }
    validateDecisionItems(sample, reviewed, errors)
  }
  return errors
}

export function createAdjudication(dataset, datasetSha256, reviewA, reviewB, reviewTextA, reviewTextB) {
  if (reviewA.reviewerId === reviewB.reviewerId) throw new Error('reviewerIds must be different')
  const aById = new Map(reviewA.samples.map((sample) => [sample.sampleId, sample]))
  const bById = new Map(reviewB.samples.map((sample) => [sample.sampleId, sample]))
  return {
    datasetVersion: dataset.datasetVersion,
    datasetSha256,
    reviewerIds: [reviewA.reviewerId, reviewB.reviewerId],
    reviewSha256: [normalizedSha256(reviewTextA), normalizedSha256(reviewTextB)],
    adjudicatorId: null,
    adjudicatedAt: null,
    samples: dataset.samples.map((sample) => {
      const decisionsA = flattenSampleDecisions(aById.get(sample.id))
      const decisionsB = flattenSampleDecisions(bById.get(sample.id))
      const keys = [...new Set([...decisionsA.keys(), ...decisionsB.keys()])].sort()
      const items = keys.map((key) => {
        const reviewerA = decisionsA.get(key)
        const reviewerB = decisionsB.get(key)
        return {
          key,
          reviewerA,
          reviewerB,
          resolution: reviewerA === reviewerB ? reviewerA : null,
          rationale: '',
        }
      })
      return { sampleId: sample.id, items }
    }),
  }
}

export function validateCompletedAdjudication(dataset, datasetSha256, reviewA, reviewB,
                                               reviewTextA, reviewTextB, adjudication) {
  const errors = []
  if (adjudication.datasetVersion !== dataset.datasetVersion) errors.push('datasetVersion mismatch')
  if (adjudication.datasetSha256 !== datasetSha256) errors.push('datasetSha256 mismatch')
  if (!Array.isArray(adjudication.reviewerIds)
      || adjudication.reviewerIds.join('|') !== [reviewA.reviewerId, reviewB.reviewerId].join('|')) {
    errors.push('reviewerIds mismatch or order changed')
  }
  const expectedHashes = [normalizedSha256(reviewTextA), normalizedSha256(reviewTextB)]
  if (!Array.isArray(adjudication.reviewSha256)
      || adjudication.reviewSha256.join('|') !== expectedHashes.join('|')) {
    errors.push('reviewSha256 mismatch')
  }
  if (!adjudication.adjudicatorId || !/^[a-z0-9][a-z0-9_-]{2,31}$/.test(adjudication.adjudicatorId)) {
    errors.push('adjudicatorId is required')
  }
  if (!isIsoDate(adjudication.adjudicatedAt)) errors.push('adjudicatedAt must be an ISO date-time')
  if (!Array.isArray(adjudication.samples) || adjudication.samples.length !== dataset.samples.length) {
    errors.push(`samples must contain ${dataset.samples.length} entries`)
    return errors
  }

  const samplesById = uniqueBy(adjudication.samples, 'sampleId', errors, 'adjudication sample')
  for (const sample of dataset.samples) {
    const entry = samplesById.get(sample.id)
    if (!entry || !Array.isArray(entry.items)) {
      errors.push(`${sample.id}: missing adjudication items`)
      continue
    }
    for (const item of entry.items) {
      if (item.resolution === null || item.resolution === undefined) {
        errors.push(`${sample.id}:${item.key}: unresolved`)
      }
      if (item.reviewerA !== item.reviewerB && !String(item.rationale ?? '').trim()) {
        errors.push(`${sample.id}:${item.key}: conflict requires rationale`)
      }
    }
  }
  return errors
}

export function createPromotionCandidate(dataset, manifest, reviewA, reviewB, adjudication) {
  const rejected = []
  for (const sample of adjudication.samples) {
    for (const item of sample.items) {
      if (item.resolution !== true && item.resolution !== 'APPROVE') {
        rejected.push(`${sample.sampleId}:${item.key}`)
      }
    }
  }
  if (rejected.length) {
    throw new Error(`cannot promote rejected decisions: ${rejected.slice(0, 10).join(', ')}`)
  }

  const promotedDataset = structuredClone(dataset)
  for (const sample of promotedDataset.samples) {
    sample.annotationStatus = 'ADJUDICATED'
    sample.annotatorIds = [reviewA.reviewerId, reviewB.reviewerId, adjudication.adjudicatorId]
  }
  const datasetText = `${JSON.stringify(promotedDataset, null, 2)}\n`
  const promotedManifest = {
    ...structuredClone(manifest),
    sampleFileSha256: normalizedSha256(datasetText),
    releaseReady: true,
    blockingReasons: [],
    reviewEvidence: {
      reviewerIds: adjudication.reviewerIds,
      reviewSha256: adjudication.reviewSha256,
      adjudicatorId: adjudication.adjudicatorId,
      adjudicatedAt: adjudication.adjudicatedAt,
      adjudicationSha256: normalizedSha256(`${JSON.stringify(adjudication, null, 2)}\n`),
    },
    changes: [...manifest.changes, 'Completed independent double review and adjudication'],
  }
  return { dataset: promotedDataset, datasetText, manifest: promotedManifest }
}

function validateDecisionItems(sample, reviewed, errors) {
  const facts = uniqueBy(reviewed.facts, 'factId', errors, `${sample.id} fact`)
  if (facts.size !== sample.referenceFacts.length) errors.push(`${sample.id}: fact review count mismatch`)
  for (const fact of sample.referenceFacts) {
    const decision = facts.get(fact.id)
    if (!decision) {
      errors.push(`${sample.id}:${fact.id}: missing fact review`)
      continue
    }
    for (const field of DECISION_FIELDS) requireBoolean(decision[field], `${sample.id}:${fact.id}:${field}`, errors)
    const support = uniqueBy(decision.sourceSupport, 'sourceId', errors, `${sample.id}:${fact.id} sourceSupport`)
    if (support.size !== fact.allowedSourceIds.length) errors.push(`${sample.id}:${fact.id}: source support count mismatch`)
    for (const sourceId of fact.allowedSourceIds) {
      const sourceDecision = support.get(sourceId)
      if (!sourceDecision) errors.push(`${sample.id}:${fact.id}:${sourceId}: missing source support`)
      else requireBoolean(sourceDecision.supports, `${sample.id}:${fact.id}:${sourceId}:supports`, errors)
    }
  }

  const sources = uniqueBy(reviewed.sources, 'sourceId', errors, `${sample.id} source`)
  if (sources.size !== sample.replaySources.length) errors.push(`${sample.id}: source review count mismatch`)
  for (const source of sample.replaySources) {
    const decision = sources.get(source.id)
    if (!decision) errors.push(`${sample.id}:${source.id}: missing source review`)
    else requireBoolean(decision.excerptSafeAndSufficient, `${sample.id}:${source.id}:excerptSafeAndSufficient`, errors)
  }

  const forbidden = new Map((reviewed.forbiddenClaims ?? []).map((item) => [item.claim, item]))
  if (forbidden.size !== sample.forbiddenClaims.length) errors.push(`${sample.id}: forbidden claim review count mismatch`)
  for (const claim of sample.forbiddenClaims) {
    const decision = forbidden.get(claim)
    if (!decision) errors.push(`${sample.id}:${claim}: missing forbidden claim review`)
    else requireBoolean(decision.appropriate, `${sample.id}:${claim}:appropriate`, errors)
  }
  if (!['APPROVE', 'REJECT'].includes(reviewed.sampleDecision)) errors.push(`${sample.id}: sampleDecision is incomplete`)
}

function flattenSampleDecisions(sample) {
  const values = new Map()
  for (const fact of sample.facts) {
    values.set(`fact:${fact.factId}:statementMatchesReplay`, fact.statementMatchesReplay)
    values.set(`fact:${fact.factId}:weightAppropriate`, fact.weightAppropriate)
    for (const source of fact.sourceSupport) values.set(`fact:${fact.factId}:source:${source.sourceId}`, source.supports)
  }
  for (const source of sample.sources) values.set(`source:${source.sourceId}:safeAndSufficient`, source.excerptSafeAndSufficient)
  for (const [index, claim] of sample.forbiddenClaims.entries()) values.set(`forbidden:${index}:appropriate`, claim.appropriate)
  values.set('sampleDecision', sample.sampleDecision)
  return values
}

function uniqueBy(values, field, errors, label) {
  const result = new Map()
  if (!Array.isArray(values)) {
    errors.push(`${label}: must be an array`)
    return result
  }
  for (const value of values) {
    const key = value?.[field]
    if (!key) errors.push(`${label}: missing ${field}`)
    else if (result.has(key)) errors.push(`${label}: duplicate ${key}`)
    else result.set(key, value)
  }
  return result
}

function requireBoolean(value, label, errors) {
  if (typeof value !== 'boolean') errors.push(`${label} is incomplete`)
}

function requireReviewerId(reviewerId) {
  if (!/^[a-z0-9][a-z0-9_-]{2,31}$/.test(reviewerId ?? '')) {
    throw new Error('reviewerId must match ^[a-z0-9][a-z0-9_-]{2,31}$')
  }
}

function isIsoDate(value) {
  return typeof value === 'string' && !Number.isNaN(Date.parse(value)) && value.includes('T')
}

function writeJson(path, value) {
  writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`, 'utf8')
}

function usage() {
  console.error('Usage:')
  console.error('  node review-workflow.mjs prepare <samples.json> <reviewer-id> <output.json>')
  console.error('  node review-workflow.mjs validate-review <samples.json> <review.json>')
  console.error('  node review-workflow.mjs adjudicate <samples.json> <review-a.json> <review-b.json> <output.json>')
  console.error('  node review-workflow.mjs validate-adjudication <samples.json> <review-a.json> <review-b.json> <adjudication.json>')
  console.error('  node review-workflow.mjs promote <samples.json> <manifest.json> <review-a.json> <review-b.json> <adjudication.json> <output-samples.json> <output-manifest.json>')
}

function datasetWithText(path) {
  const text = readFileSync(path, 'utf8')
  return { text, sha: normalizedSha256(text), value: JSON.parse(text) }
}

function reviewWithText(path) {
  const text = readFileSync(path, 'utf8')
  return { text, value: JSON.parse(text) }
}

function failOnErrors(errors) {
  if (errors.length === 0) return
  for (const error of errors) console.error(`- ${error}`)
  process.exitCode = 1
}

export function main(argv) {
  const [command, datasetPath, ...args] = argv
  if (!command || !datasetPath) {
    usage()
    process.exitCode = 2
    return
  }
  const dataset = datasetWithText(datasetPath)

  if (command === 'prepare' && args.length === 2) {
    const [reviewerId, output] = args
    writeJson(output, createReviewTemplate(dataset.value, dataset.sha, reviewerId))
    return
  }
  if (command === 'validate-review' && args.length === 1) {
    const review = loadJson(args[0])
    failOnErrors(validateCompletedReview(dataset.value, dataset.sha, review))
    return
  }
  if (command === 'adjudicate' && args.length === 3) {
    const [pathA, pathB, output] = args
    const reviewA = reviewWithText(pathA)
    const reviewB = reviewWithText(pathB)
    const reviewErrors = [
      ...validateCompletedReview(dataset.value, dataset.sha, reviewA.value),
      ...validateCompletedReview(dataset.value, dataset.sha, reviewB.value),
    ]
    if (reviewErrors.length) {
      failOnErrors(reviewErrors)
      return
    }
    writeJson(output, createAdjudication(dataset.value, dataset.sha, reviewA.value, reviewB.value,
      reviewA.text, reviewB.text))
    return
  }
  if (command === 'validate-adjudication' && args.length === 3) {
    const [pathA, pathB, adjudicationPath] = args
    const reviewA = reviewWithText(pathA)
    const reviewB = reviewWithText(pathB)
    const adjudication = loadJson(adjudicationPath)
    const errors = [
      ...validateCompletedReview(dataset.value, dataset.sha, reviewA.value),
      ...validateCompletedReview(dataset.value, dataset.sha, reviewB.value),
      ...validateCompletedAdjudication(dataset.value, dataset.sha, reviewA.value, reviewB.value,
        reviewA.text, reviewB.text, adjudication),
    ]
    failOnErrors(errors)
    return
  }
  if (command === 'promote' && args.length === 6) {
    const [manifestPath, pathA, pathB, adjudicationPath, outputSamples, outputManifest] = args
    const manifest = loadJson(manifestPath)
    const reviewA = reviewWithText(pathA)
    const reviewB = reviewWithText(pathB)
    const adjudication = loadJson(adjudicationPath)
    const errors = [
      ...validateCompletedReview(dataset.value, dataset.sha, reviewA.value),
      ...validateCompletedReview(dataset.value, dataset.sha, reviewB.value),
      ...validateCompletedAdjudication(dataset.value, dataset.sha, reviewA.value, reviewB.value,
        reviewA.text, reviewB.text, adjudication),
    ]
    if (errors.length) {
      failOnErrors(errors)
      return
    }
    const promoted = createPromotionCandidate(dataset.value, manifest, reviewA.value, reviewB.value, adjudication)
    writeFileSync(outputSamples, promoted.datasetText, 'utf8')
    writeJson(outputManifest, promoted.manifest)
    return
  }

  usage()
  process.exitCode = 2
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) main(process.argv.slice(2))
