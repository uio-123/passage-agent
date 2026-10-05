package com.passage.agent.demo;

public record DemoScenarioResult(
        String scenarioId,
        DemoScenarioType scenarioType,
        String runId,
        String runStatus,
        int versionCount,
        int artifactCount,
        int eventCount,
        boolean recoverySucceeded,
        int nodeRetryCount,
        int externalSideEffectAttempts,
        int duplicateExternalSideEffects,
        boolean reused
) { }
