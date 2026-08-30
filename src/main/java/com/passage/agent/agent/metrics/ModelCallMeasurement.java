package com.passage.agent.agent.metrics;
/** Measurement sourced from model response metadata or monotonic timing, never content-size estimation. */
public record ModelCallMeasurement(String callType,String modelName,Long inputTokens,Long outputTokens,Long totalTokens,Long firstTokenMs,Long durationMs,int retryCount,String errorCode) {
    public ModelCallMeasurement {
        if (retryCount < 0) throw new IllegalArgumentException("retryCount must not be negative");
    }
}
