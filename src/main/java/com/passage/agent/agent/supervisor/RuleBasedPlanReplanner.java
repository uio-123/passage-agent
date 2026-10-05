package com.passage.agent.agent.supervisor;

import com.passage.agent.agent.review.QualityGateDecision;
import com.passage.agent.agent.review.ReviewIssue;
import com.passage.agent.agent.review.ReviewSeverity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Deterministic first-stage planner. It interprets validated feedback into a
 * bounded action; an LLM planner can be added later behind a separate flag.
 */
@Component
public class RuleBasedPlanReplanner implements PlanReplanner {

    private static final int MAX_FEEDBACK_LENGTH = 400;

    @Override
    public ReplanResult replan(SupervisorPlan current, PlanFeedback feedback) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(feedback, "feedback");
        return feedback.source() == PlanFeedback.Source.HUMAN
                ? replanFromHuman(current, feedback.humanDecision())
                : replanFromReviewer(current, feedback);
    }

    private ReplanResult replanFromHuman(SupervisorPlan current, HumanDecision decision) {
        return switch (decision.type()) {
            case APPROVE -> next(current, PlanAction.CONTINUE, PlanRevisionReason.HUMAN_APPROVE, List.of());
            case MODIFY -> next(current, PlanAction.REPLAN, PlanRevisionReason.HUMAN_MODIFY, List.of());
            case REJECT -> next(current, PlanAction.REPLAN, PlanRevisionReason.HUMAN_REJECT, List.of());
        };
    }

    private ReplanResult replanFromReviewer(SupervisorPlan current, PlanFeedback feedback) {
        QualityGateDecision decision = feedback.reviewDecision();
        if (decision.decision() == QualityGateDecision.Decision.ACCEPT) {
            return next(current, PlanAction.CONTINUE, PlanRevisionReason.REVIEW_ACCEPTED, List.of());
        }
        if (decision.decision() == QualityGateDecision.Decision.REJECT_MAX_ROUNDS) {
            return next(current, PlanAction.STOP, PlanRevisionReason.REVISION_LIMIT, List.of());
        }

        List<ReviewIssue> factIssues = feedback.factReview().issues().stream()
                .filter(issue -> issue.severity() == ReviewSeverity.BLOCKER || issue.severity() == ReviewSeverity.MAJOR)
                .toList();
        if (!factIssues.isEmpty()) {
            return next(current, PlanAction.REQUEST_RESEARCH, PlanRevisionReason.RESEARCH_REQUIRED,
                    factIssues.stream().map(ReviewIssue::sectionId).distinct().sorted().toList());
        }

        List<ReviewIssue> revisionIssues = feedback.issues();
        if (revisionIssues.isEmpty()) {
            throw new IllegalArgumentException("REVISE feedback requires a section issue");
        }
        SupervisorPlan revised = applyRevisionInstructions(current, revisionIssues);
        List<String> affected = revisionIssues.stream().map(ReviewIssue::sectionId)
                .distinct().sorted().toList();
        return new ReplanResult(PlanAction.REVISE_SECTIONS, revised, affected);
    }

    private SupervisorPlan applyRevisionInstructions(SupervisorPlan current, List<ReviewIssue> issues) {
        Map<String, List<ReviewIssue>> bySection = issues.stream().collect(Collectors.groupingBy(ReviewIssue::sectionId));
        List<SubtaskSpec> updated = current.subtasks().stream().map(subtask -> {
            List<ReviewIssue> sectionIssues = bySection.get(subtask.id());
            if (sectionIssues == null || sectionIssues.isEmpty()) return subtask;
            String feedback = summarize(sectionIssues);
            return new SubtaskSpec(subtask.sectionIndex(), subtask.id(),
                    subtask.instruction() + " Replan feedback: " + feedback,
                    subtask.dependsOn(), subtask.requiredTools());
        }).toList();
        return new SupervisorPlan(current.version() + 1, current.version(), PlanRevisionReason.REVIEW_REVISION,
                current.researchRequired(), current.maxConcurrency(), current.maxSubtasks(),
                current.allowedTools(), updated);
    }

    private ReplanResult next(
            SupervisorPlan current,
            PlanAction action,
            PlanRevisionReason reason,
            List<String> affectedSectionIds) {
        SupervisorPlan next = new SupervisorPlan(current.version() + 1, current.version(), reason,
                current.researchRequired() || action == PlanAction.REQUEST_RESEARCH,
                current.maxConcurrency(), current.maxSubtasks(), current.allowedTools(), current.subtasks());
        return new ReplanResult(action, next, affectedSectionIds);
    }

    private String summarize(List<ReviewIssue> issues) {
        String summary = issues.stream()
                .map(issue -> issue.code() + ": " + issue.message())
                .distinct()
                .collect(Collectors.joining("; "));
        return summary.length() <= MAX_FEEDBACK_LENGTH ? summary : summary.substring(0, MAX_FEEDBACK_LENGTH);
    }
}
