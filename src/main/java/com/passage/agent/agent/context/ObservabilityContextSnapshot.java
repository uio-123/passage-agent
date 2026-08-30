package com.passage.agent.agent.context;
import java.time.Instant;
/** P4 safe operational summary; distinct from P1 ContextSnapshot workflow contract. */
public record ObservabilityContextSnapshot(String runId, long sequence, String stage, String summary,
                                           long tokenBefore, long tokenAfter, Instant createdAt) { }
