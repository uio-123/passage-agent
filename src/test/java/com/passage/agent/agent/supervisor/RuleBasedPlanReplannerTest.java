package com.passage.agent.agent.supervisor;

import com.passage.agent.agent.review.QualityGateDecision;
import com.passage.agent.agent.review.ReviewIssue;
import com.passage.agent.agent.review.ReviewReport;
import com.passage.agent.agent.review.ReviewSeverity;
import com.passage.agent.agent.review.ReviewType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedPlanReplannerTest {

    private final RuleBasedPlanReplanner replanner = new RuleBasedPlanReplanner();
    private final SupervisorPlan plan = new SupervisorPlan(false, 2, List.of(
            new SubtaskSpec(0, "section-1", "write section one")));

    @Test
    void acceptsAReviewWithoutChangingTheWorkPlan() {
        ReviewReport fact = new ReviewReport(ReviewType.FACT, 92, List.of());
        ReviewReport style = new ReviewReport(ReviewType.STYLE, 88, List.of());
        PlanFeedback feedback = PlanFeedback.reviewer(fact, style,
                new QualityGateDecision(QualityGateDecision.Decision.ACCEPT, 0, List.of()));

        ReplanResult result = replanner.replan(plan, feedback);

        assertThat(result.action()).isEqualTo(PlanAction.CONTINUE);
        assertThat(result.plan().version()).isEqualTo(2);
        assertThat(result.plan().parentVersion()).isEqualTo(1);
        assertThat(result.plan().revisionReason()).isEqualTo(PlanRevisionReason.REVIEW_ACCEPTED);
        assertThat(result.affectedSectionIds()).isEmpty();
    }

    @Test
    void turnsStyleFeedbackIntoBoundedSectionRevision() {
        ReviewIssue issue = new ReviewIssue("section-1", ReviewSeverity.MAJOR, "STYLE", "tighten the argument");
        PlanFeedback feedback = PlanFeedback.reviewer(
                new ReviewReport(ReviewType.FACT, 90, List.of()),
                new ReviewReport(ReviewType.STYLE, 70, List.of(issue)),
                new QualityGateDecision(QualityGateDecision.Decision.REVISE, 1, List.of(issue)));

        ReplanResult result = replanner.replan(plan, feedback);

        assertThat(result.action()).isEqualTo(PlanAction.REVISE_SECTIONS);
        assertThat(result.affectedSectionIds()).containsExactly("section-1");
        assertThat(result.plan().revisionReason()).isEqualTo(PlanRevisionReason.REVIEW_REVISION);
        assertThat(result.plan().subtasks().getFirst().instruction()).contains("STYLE: tighten the argument");
    }

    @Test
    void turnsBlockingFactFeedbackIntoResearchAndStopsAtTheRevisionLimit() {
        ReviewIssue factIssue = new ReviewIssue("section-1", ReviewSeverity.BLOCKER, "UNSUPPORTED", "missing source");
        PlanFeedback research = PlanFeedback.reviewer(
                new ReviewReport(ReviewType.FACT, 60, List.of(factIssue)),
                new ReviewReport(ReviewType.STYLE, 90, List.of()),
                new QualityGateDecision(QualityGateDecision.Decision.REVISE, 1, List.of(factIssue)));

        ReplanResult researchResult = replanner.replan(plan, research);
        assertThat(researchResult.action()).isEqualTo(PlanAction.REQUEST_RESEARCH);
        assertThat(researchResult.plan().researchRequired()).isTrue();
        assertThat(researchResult.plan().revisionReason()).isEqualTo(PlanRevisionReason.RESEARCH_REQUIRED);

        PlanFeedback stopped = PlanFeedback.reviewer(
                new ReviewReport(ReviewType.FACT, 60, List.of(factIssue)),
                new ReviewReport(ReviewType.STYLE, 90, List.of()),
                new QualityGateDecision(QualityGateDecision.Decision.REJECT_MAX_ROUNDS, 2, List.of(factIssue)));
        ReplanResult stopResult = replanner.replan(plan, stopped);
        assertThat(stopResult.action()).isEqualTo(PlanAction.STOP);
        assertThat(stopResult.plan().revisionReason()).isEqualTo(PlanRevisionReason.REVISION_LIMIT);
    }

    @Test
    void mapsHumanApproveModifyAndRejectIntoBoundedActions() {
        ReplanResult approved = replanner.replan(plan,
                PlanFeedback.human(new HumanDecision(HumanDecision.Type.APPROVE, null)));
        ReplanResult modified = replanner.replan(plan,
                PlanFeedback.human(new HumanDecision(HumanDecision.Type.MODIFY, "write a shorter article")));
        ReplanResult rejected = replanner.replan(plan,
                PlanFeedback.human(new HumanDecision(HumanDecision.Type.REJECT, "topic is not acceptable")));

        assertThat(approved.action()).isEqualTo(PlanAction.CONTINUE);
        assertThat(approved.plan().revisionReason()).isEqualTo(PlanRevisionReason.HUMAN_APPROVE);
        assertThat(modified.action()).isEqualTo(PlanAction.REPLAN);
        assertThat(modified.plan().revisionReason()).isEqualTo(PlanRevisionReason.HUMAN_MODIFY);
        assertThat(rejected.action()).isEqualTo(PlanAction.REPLAN);
        assertThat(rejected.plan().revisionReason()).isEqualTo(PlanRevisionReason.HUMAN_REJECT);
    }
}
