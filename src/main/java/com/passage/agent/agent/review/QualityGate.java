package com.passage.agent.agent.review;

import java.util.List;

/** Deterministic gate that accepts, requests bounded local revision, or terminates. */
public final class QualityGate {
    private final QualityGatePolicy policy;
    public QualityGate(QualityGatePolicy policy) { this.policy = policy; }

    public QualityGateDecision evaluate(ParallelSectionReviewUseCase.ReviewPair reviews, int completedRevisionRounds) {
        if (reviews == null || completedRevisionRounds < 0) throw new IllegalArgumentException("Quality gate input is invalid");
        List<ReviewIssue> issues = java.util.stream.Stream.concat(reviews.fact().issues().stream(), reviews.style().issues().stream()).toList();
        boolean passes = reviews.fact().score() >= policy.minFactScore() && reviews.style().score() >= policy.minStyleScore()
                && issues.stream().noneMatch(issue -> issue.severity() == ReviewSeverity.BLOCKER);
        if (passes) return new QualityGateDecision(QualityGateDecision.Decision.ACCEPT, completedRevisionRounds, issues);
        if (completedRevisionRounds >= policy.maxRevisionRounds()) {
            return new QualityGateDecision(QualityGateDecision.Decision.REJECT_MAX_ROUNDS, completedRevisionRounds, issues);
        }
        return new QualityGateDecision(QualityGateDecision.Decision.REVISE, completedRevisionRounds + 1, issues);
    }
}
