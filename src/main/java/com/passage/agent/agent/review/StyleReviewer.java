package com.passage.agent.agent.review;

import com.passage.agent.agent.writing.SectionDraft;

@FunctionalInterface
public interface StyleReviewer {
    ReviewReport review(SectionDraft draft);
}
