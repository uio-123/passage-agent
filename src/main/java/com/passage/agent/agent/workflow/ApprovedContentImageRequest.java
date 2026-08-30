package com.passage.agent.agent.workflow;

import java.util.List;

/** The only inputs allowed to the post-quality image workflow. */
public record ApprovedContentImageRequest(
        String runId,
        String mainTitle,
        String subTitle,
        String style,
        List<String> enabledImageMethods,
        String acceptedMarkdown
) {
    public ApprovedContentImageRequest {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId must not be blank");
        if (mainTitle == null || mainTitle.isBlank()) throw new IllegalArgumentException("mainTitle must not be blank");
        if (acceptedMarkdown == null || acceptedMarkdown.isBlank()) throw new IllegalArgumentException("acceptedMarkdown must not be blank");
        enabledImageMethods = enabledImageMethods == null ? List.of() : List.copyOf(enabledImageMethods);
    }
}
