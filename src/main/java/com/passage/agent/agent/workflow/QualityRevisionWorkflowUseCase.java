package com.passage.agent.agent.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passage.agent.agent.artifact.ArticleArtifactManifestFactory;
import com.passage.agent.agent.artifact.ArticleVersion;
import com.passage.agent.agent.artifact.ArticleVersionChain;
import com.passage.agent.agent.research.ResearchBundle;
import com.passage.agent.agent.review.FactChecker;
import com.passage.agent.agent.review.ParallelSectionReviewUseCase;
import com.passage.agent.agent.review.QualityGate;
import com.passage.agent.agent.review.QualityGateDecision;
import com.passage.agent.agent.review.QualityGatePolicy;
import com.passage.agent.agent.review.ReviewIssue;
import com.passage.agent.agent.review.StyleReviewer;
import com.passage.agent.agent.revision.RevisionAgent;
import com.passage.agent.agent.revision.RevisionResult;
import com.passage.agent.agent.revision.SectionRevisionUseCase;
import com.passage.agent.agent.writing.ParallelSectionWritingUseCase;
import com.passage.agent.agent.writing.SectionDraft;
import com.passage.agent.agent.writing.SectionDraftValidator;
import com.passage.agent.agent.writing.SectionFanIn;
import com.passage.agent.agent.writing.SectionTask;
import com.passage.agent.service.AgentArticleArtifactService;
import com.passage.agent.service.AgentNodeExecutionService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * P3's bounded quality loop. Every Revision and Artifact publication is placed
 * behind the existing durable node-execution boundary, so a retry reuses work.
 */
@Service
public class QualityRevisionWorkflowUseCase {
    private final ParallelSectionWritingUseCase writing;
    private final ParallelSectionReviewUseCase reviewing;
    private final AgentNodeExecutionService nodes;
    private final AgentArticleArtifactService artifacts;
    private final ObjectMapper objectMapper;
    private final QualityGate gate = new QualityGate(QualityGatePolicy.strictDefaults());
    private final SectionRevisionUseCase revision = new SectionRevisionUseCase(new SectionDraftValidator(), new SectionFanIn());
    private final ArticleArtifactManifestFactory manifestFactory = new ArticleArtifactManifestFactory();

    public QualityRevisionWorkflowUseCase(ParallelSectionWritingUseCase writing, ParallelSectionReviewUseCase reviewing,
                                          AgentNodeExecutionService nodes, AgentArticleArtifactService artifacts,
                                          ObjectMapper objectMapper) {
        this.writing = writing;
        this.reviewing = reviewing;
        this.nodes = nodes;
        this.artifacts = artifacts;
        this.objectMapper = objectMapper;
    }

    public QualityWorkflowResult execute(String runId, long stateVersion, int maxConcurrency, ResearchBundle research,
                                         List<SectionTask> tasks, com.passage.agent.agent.writing.SectionWriter writer,
                                         FactChecker factChecker, StyleReviewer styleReviewer, RevisionAgent revisionAgent) {
        if (runId == null || runId.isBlank() || stateVersion < 0 || research == null || writer == null
                || factChecker == null || styleReviewer == null || revisionAgent == null) {
            throw new IllegalArgumentException("Quality workflow inputs are invalid");
        }
        List<SectionDraft> drafts = writing.execute(runId, stateVersion, maxConcurrency, research, tasks, writer).drafts();
        if (drafts.isEmpty()) throw new IllegalArgumentException("Quality workflow requires at least one section draft");
        ArticleVersionChain versions = ArticleVersionChain.initial(drafts, "initial draft", Instant.now());
        int completedRounds = 0;
        while (true) {
            QualityGateDecision decision = evaluate(drafts, research, factChecker, styleReviewer, completedRounds);
            if (decision.decision() == QualityGateDecision.Decision.REJECT_MAX_ROUNDS) {
                return new QualityWorkflowResult(decision, versions, drafts);
            }
            publish(runId, stateVersion + completedRounds, versions.latest(), research, decision);
            if (decision.decision() == QualityGateDecision.Decision.ACCEPT) {
                return new QualityWorkflowResult(decision, versions, drafts);
            }
            RevisionResult revised = revision.revise(tasks, drafts, research, decision, request -> deserialize(nodes.executeOnce(
                    runId, "section-revision-" + request.originalDraft().sectionId(), stateVersion + decision.completedRevisionRounds(),
                    () -> serialize(revisionAgent.revise(request))).resultSnapshot()));
            drafts = revised.drafts();
            completedRounds = decision.completedRevisionRounds();
            versions = versions.append(revised, "quality gate revision round " + completedRounds, Instant.now());
        }
    }

    private QualityGateDecision evaluate(List<SectionDraft> drafts, ResearchBundle research, FactChecker factChecker,
                                         StyleReviewer styleReviewer, int completedRounds) {
        List<QualityGateDecision> decisions = drafts.stream().map(draft -> gate.evaluate(
                reviewing.review(draft, research, factChecker, styleReviewer), completedRounds)).toList();
        List<ReviewIssue> issues = decisions.stream().flatMap(decision -> decision.issues().stream()).toList();
        if (decisions.stream().anyMatch(decision -> decision.decision() == QualityGateDecision.Decision.REJECT_MAX_ROUNDS)) {
            return new QualityGateDecision(QualityGateDecision.Decision.REJECT_MAX_ROUNDS, completedRounds, issues);
        }
        if (decisions.stream().anyMatch(decision -> decision.decision() == QualityGateDecision.Decision.REVISE)) {
            if (issues.isEmpty()) throw new IllegalArgumentException("A REVISE decision requires at least one section issue");
            return new QualityGateDecision(QualityGateDecision.Decision.REVISE, completedRounds + 1, issues);
        }
        return new QualityGateDecision(QualityGateDecision.Decision.ACCEPT, completedRounds, issues);
    }

    private void publish(String runId, long versionState, ArticleVersion version, ResearchBundle research, QualityGateDecision decision) {
        nodes.executeOnce(runId, "article-artifact-v" + version.version(), versionState, () -> {
            artifacts.publish(runId, version, manifestFactory.create(runId, version, research, decision, Instant.now()));
            return "{\"publishedVersion\":" + version.version() + "}";
        });
    }

    private String serialize(SectionDraft draft) {
        try { return objectMapper.writeValueAsString(draft); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Revision draft must be serializable", exception); }
    }

    private SectionDraft deserialize(String snapshot) {
        try { return objectMapper.readValue(snapshot, SectionDraft.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Revision snapshot is invalid", exception); }
    }

    public record QualityWorkflowResult(QualityGateDecision decision, ArticleVersionChain versions, List<SectionDraft> drafts) {
        public QualityWorkflowResult { drafts = List.copyOf(drafts); }
    }
}
