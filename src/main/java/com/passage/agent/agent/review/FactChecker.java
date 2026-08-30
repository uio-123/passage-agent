package com.passage.agent.agent.review;

@FunctionalInterface
public interface FactChecker {
    ReviewReport review(FactReviewRequest request);
}
