package com.passage.agent.agent.api;

/** Stable, application-level stage identifiers for article workflow results. */
public enum WorkflowStage {
    TITLES_GENERATED,
    OUTLINE_GENERATED,
    /** P3 writing/review passed; image generation and final article delivery are still pending. */
    CONTENT_QUALITY_ACCEPTED,
    ARTICLE_COMPLETED
}
