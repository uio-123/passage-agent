package com.passage.agent.agent.workflow;

import java.util.function.Consumer;

/** Executes only the image half of article delivery for accepted P3 markdown. */
public interface ApprovedContentImageExecutor {
    ApprovedContentImageResult execute(ApprovedContentImageRequest request, Consumer<String> streamHandler);
}
