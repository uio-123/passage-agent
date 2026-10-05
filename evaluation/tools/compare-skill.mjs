import { readFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

export function compareSkillRegression(baselineSummary, candidateSummary, variant = 'multi_agent_full') {
  const baseline = baselineSummary.variants.find((item) => item.variant === variant)
  const candidate = candidateSummary.variants.find((item) => item.variant === variant)
  if (!baseline || !candidate) throw new Error(`variant ${variant} missing from summary`)
  const checks = {
    macroFactSupportDropWithinTwoPoints: boundedDrop(baseline.macroFactSupportRate, candidate.macroFactSupportRate, 0.02),
    macroCitationValidityDropWithinTwoPoints: boundedDrop(baseline.macroCitationValidityRate, candidate.macroCitationValidityRate, 0.02),
    qualityMedianDropWithinTwoPoints: boundedDrop(baseline.qualityMedian, candidate.qualityMedian, 2),
    noCriticalFailures: candidate.criticalFailureCount === 0,
  }
  const unavailable = Object.values(checks).some((value) => value === null)
  return {
    reportStatus: 'DRAFT',
    publicationStatus: 'NON_RELEASE',
    variant,
    status: unavailable ? 'INCOMPLETE_DRAFT' : (Object.values(checks).every(Boolean) ? 'DRAFT_PASS' : 'DRAFT_FAIL'),
    checks,
  }
}

function boundedDrop(baseline, candidate, tolerance) {
  if (baseline === null || candidate === null || baseline === undefined || candidate === undefined) return null
  return candidate >= baseline - tolerance
}

export function main(argv) {
  if (argv.length < 2 || argv.length > 3) {
    console.error('Usage: node compare-skill.mjs <baseline-summary.json> <candidate-summary.json> [variant]')
    process.exitCode = 2
    return
  }
  const result = compareSkillRegression(JSON.parse(readFileSync(argv[0], 'utf8')),
    JSON.parse(readFileSync(argv[1], 'utf8')), argv[2] ?? 'multi_agent_full')
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`)
  if (result.status === 'DRAFT_FAIL') process.exitCode = 1
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) main(process.argv.slice(2))
