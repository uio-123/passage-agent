package com.passage.agent.agent.review;

import com.passage.agent.agent.research.ResearchBundle;
import com.passage.agent.agent.research.ResearchSource;
import com.passage.agent.agent.research.ResearchSourceStatus;
import com.passage.agent.agent.writing.SectionDraft;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class QualityGateContractTest {
    private final ResearchBundle research = new ResearchBundle("research", List.of(new ResearchSource(
            "source-1", "https://example.com/evidence", "Evidence", null, Instant.EPOCH,
            "hash", "summary", "query", ResearchSourceStatus.VERIFIED)), List.of(), List.of("none"));
    private final SectionDraft draft = new SectionDraft(0, "section", "content", List.of("source-1"));

    @Test
    void runsTypedFactAndStyleReviewersAndAcceptsPassingReports() {
        ParallelSectionReviewUseCase reviews = new ParallelSectionReviewUseCase();
        var pair = reviews.review(draft, research,
                request -> new ReviewReport(ReviewType.FACT, 90, List.of()),
                section -> new ReviewReport(ReviewType.STYLE, 85, List.of()));

        var decision = new QualityGate(QualityGatePolicy.strictDefaults()).evaluate(pair, 0);
        assertThat(decision.decision()).isEqualTo(QualityGateDecision.Decision.ACCEPT);
    }

    @Test
    void requestsAtMostTwoRevisionRoundsThenTerminates() {
        ReviewIssue issue = new ReviewIssue("section", ReviewSeverity.BLOCKER, "UNSUPPORTED", "needs evidence");
        var failed = new ParallelSectionReviewUseCase.ReviewPair(
                new ReviewReport(ReviewType.FACT, 70, List.of(issue)), new ReviewReport(ReviewType.STYLE, 90, List.of()));
        QualityGate gate = new QualityGate(QualityGatePolicy.strictDefaults());

        assertThat(gate.evaluate(failed, 0).decision()).isEqualTo(QualityGateDecision.Decision.REVISE);
        assertThat(gate.evaluate(failed, 1).decision()).isEqualTo(QualityGateDecision.Decision.REVISE);
        assertThat(gate.evaluate(failed, 2).decision()).isEqualTo(QualityGateDecision.Decision.REJECT_MAX_ROUNDS);
    }

    @Test
    void rejectsFactReviewWhenDraftCitationIsNotInResearchBundle() {
        SectionDraft invented = new SectionDraft(0, "section", "content", List.of("invented"));
        assertThatIllegalArgumentException().isThrownBy(() -> new FactReviewRequest(invented, research));
    }
}
