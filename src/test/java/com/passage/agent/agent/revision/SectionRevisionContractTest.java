package com.passage.agent.agent.revision;

import com.passage.agent.agent.artifact.ArticleArtifactManifestFactory;
import com.passage.agent.agent.artifact.ArticleVersionChain;
import com.passage.agent.agent.artifact.ArtifactType;
import com.passage.agent.agent.research.ResearchBundle;
import com.passage.agent.agent.research.ResearchSource;
import com.passage.agent.agent.research.ResearchSourceStatus;
import com.passage.agent.agent.review.QualityGateDecision;
import com.passage.agent.agent.review.ReviewIssue;
import com.passage.agent.agent.review.ReviewSeverity;
import com.passage.agent.agent.writing.SectionDraft;
import com.passage.agent.agent.writing.SectionDraftValidator;
import com.passage.agent.agent.writing.SectionFanIn;
import com.passage.agent.agent.writing.SectionTask;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SectionRevisionContractTest {
    private final ResearchBundle research = new ResearchBundle("research-run", List.of(
            new ResearchSource("source-1", "https://example.com/one", "One", "Example", Instant.EPOCH,
                    "hash-1", "Evidence", "query", ResearchSourceStatus.VERIFIED)
    ), List.of("fact"), List.of());
    private final List<SectionTask> tasks = List.of(
            new SectionTask(0, "first", "First", "Write first", List.of("source-1")),
            new SectionTask(1, "second", "Second", "Write second", List.of("source-1")));
    private final List<SectionDraft> drafts = List.of(
            new SectionDraft(0, "first", "first original", List.of("source-1")),
            new SectionDraft(1, "second", "second original", List.of("source-1")));

    @Test
    void revisesOnlyGateSelectedSectionsAndPreservesTheRestByteForByte() {
        SectionRevisionUseCase useCase = new SectionRevisionUseCase(new SectionDraftValidator(), new SectionFanIn());
        QualityGateDecision gate = revise("second", "STYLE", 1);

        RevisionResult result = useCase.revise(tasks, drafts, research, gate, request ->
                new SectionDraft(request.originalDraft().sectionIndex(), request.originalDraft().sectionId(),
                        "second revised", List.of("source-1")));

        assertThat(result.revisedSectionIds()).containsExactly("second");
        assertThat(result.drafts()).extracting(SectionDraft::markdown).containsExactly("first original", "second revised");
        assertThat(result.drafts().getFirst()).isSameAs(drafts.getFirst());
    }

    @Test
    void rejectsUnknownGateTargetsAndNonRevisionDecisions() {
        SectionRevisionUseCase useCase = new SectionRevisionUseCase(new SectionDraftValidator(), new SectionFanIn());
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.revise(tasks, drafts, research,
                revise("missing", "FACT", 0), request -> request.originalDraft()));
        assertThatIllegalArgumentException().isThrownBy(() -> useCase.revise(tasks, drafts, research,
                new QualityGateDecision(QualityGateDecision.Decision.ACCEPT, 0, List.of()), request -> request.originalDraft()));
    }

    @Test
    void appendsImmutableVersionsAndCreatesHashedManifest() {
        RevisionResult revision = new RevisionResult(List.of(
                drafts.getFirst(), new SectionDraft(1, "second", "second revised", List.of("source-1"))), List.of("second"), 1);
        ArticleVersionChain initial = ArticleVersionChain.initial(drafts, "initial draft", Instant.EPOCH);
        ArticleVersionChain updated = initial.append(revision, "STYLE: second", Instant.EPOCH.plusSeconds(1));

        assertThat(initial.latest().version()).isEqualTo(1);
        assertThat(updated.latest().version()).isEqualTo(2);
        assertThat(updated.latest().parentVersion()).isEqualTo(1);
        var manifest = new ArticleArtifactManifestFactory().create("run-1", updated.latest(), research,
                revise("second", "STYLE", 1), Instant.EPOCH.plusSeconds(2));
        assertThat(manifest.artifacts()).extracting(artifact -> artifact.type())
                .containsExactly(ArtifactType.ARTICLE_MARKDOWN, ArtifactType.SOURCE_BUNDLE, ArtifactType.QUALITY_REPORT);
        assertThat(manifest.artifacts()).allSatisfy(artifact -> assertThat(artifact.sha256()).matches("[0-9a-f]{64}"));
    }

    private static QualityGateDecision revise(String sectionId, String code, int rounds) {
        return new QualityGateDecision(QualityGateDecision.Decision.REVISE, rounds,
                List.of(new ReviewIssue(sectionId, ReviewSeverity.MAJOR, code, "Fix this section")));
    }
}
