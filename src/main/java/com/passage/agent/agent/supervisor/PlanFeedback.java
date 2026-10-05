package com.passage.agent.agent.supervisor;

import com.passage.agent.agent.review.QualityGateDecision;
import com.passage.agent.agent.review.ReviewIssue;
import com.passage.agent.agent.review.ReviewReport;
import com.passage.agent.agent.review.ReviewType;

import java.util.List;
import java.util.Objects;

/** Typed reviewer or human feedback consumed by the deterministic planner. */
public record PlanFeedback(
        Source source,
        ReviewReport factReview,
        ReviewReport styleReview,
        QualityGateDecision reviewDecision,
        HumanDecision humanDecision
) {
    public PlanFeedback {
        Objects.requireNonNull(source, "source");
        if (source == Source.REVIEWER) {
            Objects.requireNonNull(factReview, "factReview");
            Objects.requireNonNull(styleReview, "styleReview");
            Objects.requireNonNull(reviewDecision, "reviewDecision");
            if (factReview.type() != ReviewType.FACT || styleReview.type() != ReviewType.STYLE) {
                throw new IllegalArgumentException("review reports have invalid types");
            }
            if (humanDecision != null) {
                throw new IllegalArgumentException("reviewer feedback cannot contain a human decision");
            }
        } else if (humanDecision == null || factReview != null || styleReview != null || reviewDecision != null) {
            throw new IllegalArgumentException("human feedback must contain only a human decision");
        }
    }

    public static PlanFeedback reviewer(
            ReviewReport factReview,
            ReviewReport styleReview,
            QualityGateDecision reviewDecision) {
        return new PlanFeedback(Source.REVIEWER, factReview, styleReview, reviewDecision, null);
    }

    public static PlanFeedback human(HumanDecision decision) {
        return new PlanFeedback(Source.HUMAN, null, null, null, decision);
    }

    public List<ReviewIssue> issues() {
        return source == Source.REVIEWER ? reviewDecision.issues() : List.of();
    }

    public enum Source {
        REVIEWER,
        HUMAN
    }
}
