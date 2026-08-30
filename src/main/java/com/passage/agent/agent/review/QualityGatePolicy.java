package com.passage.agent.agent.review;

/** Code-owned thresholds and revision ceiling for P3; reviewers cannot change either. */
public record QualityGatePolicy(int minFactScore, int minStyleScore, int maxRevisionRounds) {
    public QualityGatePolicy {
        if (minFactScore < 0 || minFactScore > 100 || minStyleScore < 0 || minStyleScore > 100 || maxRevisionRounds < 1) {
            throw new IllegalArgumentException("Quality gate policy is invalid");
        }
    }
    public static QualityGatePolicy strictDefaults() { return new QualityGatePolicy(80, 80, 2); }
}
