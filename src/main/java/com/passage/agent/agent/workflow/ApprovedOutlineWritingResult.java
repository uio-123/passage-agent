package com.passage.agent.agent.workflow;

import com.passage.agent.agent.artifact.ArticleVersion;
import com.passage.agent.agent.review.QualityGateDecision;
import com.passage.agent.agent.writing.SectionDraft;
import java.util.List;

/** Main-flow-safe P3 result: stable Markdown plus only the delivery metadata Runner needs. */
public record ApprovedOutlineWritingResult(String markdown, QualityGateDecision decision, ArticleVersion latestVersion,
                                           List<SectionDraft> drafts) {
    public ApprovedOutlineWritingResult { drafts = List.copyOf(drafts); }
}
