package com.passage.agent.agent.workflow;

import com.passage.agent.agent.review.FactChecker;
import com.passage.agent.agent.review.StyleReviewer;
import com.passage.agent.agent.revision.RevisionAgent;
import com.passage.agent.agent.writing.SectionWriter;
import org.springframework.stereotype.Service;

/** Keeps P3 port wiring out of ArticleWorkflowRunner and its legacy StateGraph adapter. */
@Service
public class P3ContentWorkflowAdapter implements P3ContentWorkflow {
    private final QualityRevisionWorkflowUseCase qualityLoop;
    private final SectionWriter writer;
    private final FactChecker factChecker;
    private final StyleReviewer styleReviewer;
    private final RevisionAgent revisionAgent;
    public P3ContentWorkflowAdapter(QualityRevisionWorkflowUseCase qualityLoop, SectionWriter writer, FactChecker factChecker,
                                    StyleReviewer styleReviewer, RevisionAgent revisionAgent) {
        this.qualityLoop = qualityLoop; this.writer = writer; this.factChecker = factChecker;
        this.styleReviewer = styleReviewer; this.revisionAgent = revisionAgent;
    }
    @Override public ApprovedOutlineWritingResult execute(ApprovedOutlineWritingRequest request, int maxConcurrency) {
        var result = qualityLoop.execute(request.runId(), request.stateVersion(), maxConcurrency, request.research(), request.sectionTasks(),
                writer, factChecker, styleReviewer, revisionAgent);
        String markdown = result.drafts().stream().map(draft -> draft.markdown()).collect(java.util.stream.Collectors.joining("\n\n"));
        return new ApprovedOutlineWritingResult(markdown, result.decision(), result.versions().latest(), result.drafts());
    }
}
