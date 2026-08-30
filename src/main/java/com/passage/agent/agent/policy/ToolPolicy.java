package com.passage.agent.agent.policy;

import java.time.Duration;

/** Fixed limits applied by the only public research-tool execution boundary. */
public record ToolPolicy(
        Duration connectTimeout,
        Duration readTimeout,
        int maxResponseBytes,
        int maxTextCharacters,
        int maxRedirects,
        int maxRetries,
        int maxUrlLength
) {
    public ToolPolicy {
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("timeouts must be positive");
        }
        if (maxResponseBytes < 1 || maxTextCharacters < 1 || maxRedirects < 0 || maxRetries < 0 || maxUrlLength < 1) {
            throw new IllegalArgumentException("tool policy limits are invalid");
        }
    }

    public static ToolPolicy strictDefaults() {
        return new ToolPolicy(Duration.ofSeconds(3), Duration.ofSeconds(5), 1_000_000, 100_000, 3, 1, 2_048);
    }
}
