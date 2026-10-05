import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import {
  createAdjudication,
  createPromotionCandidate,
  createReviewTemplate,
  normalizedSha256,
  validateCompletedAdjudication,
  validateCompletedReview,
} from './review-workflow.mjs'

const datasetText = readFileSync(new URL('../datasets/p5-v1/samples.json', import.meta.url), 'utf8')
const dataset = JSON.parse(datasetText)
const datasetSha = normalizedSha256(datasetText)

function completeReview(reviewerId) {
  const review = createReviewTemplate(dataset, datasetSha, reviewerId)
  review.reviewedAt = '2026-08-31T01:00:00+08:00'
  for (const sample of review.samples) {
    for (const fact of sample.facts) {
      fact.statementMatchesReplay = true
      fact.weightAppropriate = true
      for (const source of fact.sourceSupport) source.supports = true
    }
    for (const source of sample.sources) source.excerptSafeAndSufficient = true
    for (const claim of sample.forbiddenClaims) claim.appropriate = true
    sample.sampleDecision = 'APPROVE'
  }
  return review
}

test('template pins dataset and incomplete review is rejected', () => {
  const review = createReviewTemplate(dataset, datasetSha, 'reviewer-a')
  assert.equal(review.samples.length, 30)
  assert.equal(review.datasetSha256, datasetSha)
  assert.ok(validateCompletedReview(dataset, datasetSha, review).length > 30)
})

test('two complete independent reviews can be adjudicated and verified', () => {
  const reviewA = completeReview('reviewer-a')
  const reviewB = completeReview('reviewer-b')
  assert.deepEqual(validateCompletedReview(dataset, datasetSha, reviewA), [])
  assert.deepEqual(validateCompletedReview(dataset, datasetSha, reviewB), [])

  const textA = `${JSON.stringify(reviewA, null, 2)}\n`
  const textB = `${JSON.stringify(reviewB, null, 2)}\n`
  const adjudication = createAdjudication(dataset, datasetSha, reviewA, reviewB, textA, textB)
  assert.equal(adjudication.samples.length, 30)
  assert.ok(adjudication.samples.every((sample) => sample.items.every((item) => item.resolution !== null)))

  adjudication.adjudicatorId = 'adjudicator-1'
  adjudication.adjudicatedAt = '2026-08-31T02:00:00+08:00'
  assert.deepEqual(validateCompletedAdjudication(
    dataset, datasetSha, reviewA, reviewB, textA, textB, adjudication,
  ), [])

  const manifest = {
    datasetVersion: 'p5-v1', sampleFileSha256: datasetSha, releaseReady: false,
    blockingReasons: ['review required'], changes: ['initial draft'],
  }
  const promoted = createPromotionCandidate(dataset, manifest, reviewA, reviewB, adjudication)
  assert.equal(promoted.manifest.releaseReady, true)
  assert.deepEqual(promoted.manifest.blockingReasons, [])
  assert.equal(promoted.manifest.sampleFileSha256, normalizedSha256(promoted.datasetText))
  assert.ok(promoted.dataset.samples.every((sample) => sample.annotationStatus === 'ADJUDICATED'))
  assert.ok(promoted.dataset.samples.every((sample) => sample.annotatorIds.length === 3))
})

test('a disagreement remains unresolved and requires rationale', () => {
  const reviewA = completeReview('reviewer-a')
  const reviewB = completeReview('reviewer-b')
  reviewB.samples[0].facts[0].weightAppropriate = false
  const textA = `${JSON.stringify(reviewA, null, 2)}\n`
  const textB = `${JSON.stringify(reviewB, null, 2)}\n`
  const adjudication = createAdjudication(dataset, datasetSha, reviewA, reviewB, textA, textB)
  adjudication.adjudicatorId = 'adjudicator-1'
  adjudication.adjudicatedAt = '2026-08-31T02:00:00+08:00'
  const errors = validateCompletedAdjudication(dataset, datasetSha, reviewA, reviewB,
    textA, textB, adjudication)
  assert.ok(errors.some((error) => error.includes('unresolved')))

  adjudication.samples[0].items.find((item) => item.resolution === null).resolution = false
  adjudication.samples[0].items.find((item) => item.reviewerA !== item.reviewerB).rationale = '权重需要修改后重新复核'
  assert.throws(
    () => createPromotionCandidate(dataset, { changes: [] }, reviewA, reviewB, adjudication),
    /cannot promote rejected decisions/,
  )
})
