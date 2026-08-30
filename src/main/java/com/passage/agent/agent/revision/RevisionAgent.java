package com.passage.agent.agent.revision;

import com.passage.agent.agent.writing.SectionDraft;

/** Revises a single Gate-selected section; it never receives the full article. */
@FunctionalInterface
public interface RevisionAgent {
    SectionDraft revise(SectionRevisionRequest request);
}
