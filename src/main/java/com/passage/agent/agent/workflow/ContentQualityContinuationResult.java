package com.passage.agent.agent.workflow;

/** Public-safe result of continuing an accepted content-quality checkpoint. */
public record ContentQualityContinuationResult(String runId, String stage, String fullContent) { }
