package com.passage.agent.agent.review;

import java.util.List;

/** Structured review result with a bounded score and actionable findings. */
public record ReviewReport(ReviewType type, int score, List<ReviewIssue> issues) {
    public ReviewReport {
        if (type == null || score < 0 || score > 100) throw new IllegalArgumentException("review type and score are invalid");
        issues = issues == null ? List.of() : List.copyOf(issues);
        if (issues.stream().anyMatch(issue -> issue == null)) throw new IllegalArgumentException("issues must not contain null");
    }
}
