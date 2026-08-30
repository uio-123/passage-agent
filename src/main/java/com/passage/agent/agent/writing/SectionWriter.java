package com.passage.agent.agent.writing;

/** P3 Writer port. Implementations may use a model but must return a validated structured draft. */
@FunctionalInterface
public interface SectionWriter {
    SectionDraft write(SectionWriterRequest request);
}
