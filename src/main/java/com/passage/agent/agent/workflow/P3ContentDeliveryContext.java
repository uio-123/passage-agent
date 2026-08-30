package com.passage.agent.agent.workflow;

import java.util.List;

/** Stable, already-approved inputs required to continue from the quality boundary into image delivery. */
public record P3ContentDeliveryContext(
        String mainTitle,
        String subTitle,
        String style,
        List<String> enabledImageMethods
) {
    public P3ContentDeliveryContext {
        if (mainTitle == null || mainTitle.isBlank()) {
            throw new IllegalArgumentException("mainTitle must not be blank");
        }
        enabledImageMethods = enabledImageMethods == null ? List.of() : List.copyOf(enabledImageMethods);
    }
}
