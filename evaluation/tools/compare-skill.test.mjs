import assert from 'node:assert/strict'
import test from 'node:test'
import { compareSkillRegression } from './compare-skill.mjs'

function summary(fsr, cvr, quality, critical = 0) {
  return { variants: [{ variant: 'multi_agent_full', macroFactSupportRate: fsr,
    macroCitationValidityRate: cvr, qualityMedian: quality, criticalFailureCount: critical }] }
}

test('skill regression accepts drops of at most two absolute points', () => {
  const result = compareSkillRegression(summary(0.92, 0.97, 84), summary(0.90, 0.95, 82))
  assert.equal(result.status, 'DRAFT_PASS')
  assert.equal(result.publicationStatus, 'NON_RELEASE')
})

test('skill regression fails on larger drop or a critical failure', () => {
  assert.equal(compareSkillRegression(summary(0.92, 0.97, 84), summary(0.89, 0.95, 82)).status, 'DRAFT_FAIL')
  assert.equal(compareSkillRegression(summary(0.92, 0.97, 84), summary(0.92, 0.97, 84, 1)).status, 'DRAFT_FAIL')
})

test('missing metrics remain incomplete instead of being treated as zero', () => {
  assert.equal(compareSkillRegression(summary(0.92, 0.97, 84), summary(null, 0.97, 84)).status, 'INCOMPLETE_DRAFT')
})
