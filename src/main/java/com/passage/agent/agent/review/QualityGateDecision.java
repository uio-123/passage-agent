package com.passage.agent.agent.review;

import java.util.List;

public record QualityGateDecision(Decision decision, int completedRevisionRounds, List<ReviewIssue> issues) {
    public enum Decision { ACCEPT, REVISE, REJECT_MAX_ROUNDS }
    public QualityGateDecision {
        if (decision == null || completedRevisionRounds < 0) throw new IllegalArgumentException("Quality gate decision is invalid");
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
