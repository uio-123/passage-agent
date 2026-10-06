package com.passage.agent.agent.llm;

import java.time.Duration;

/** Project-owned failure for a model call that exceeded its end-to-end deadline. */
public class ModelCallTimeoutException extends RuntimeException {

    private final String operation;
    private final Duration timeout;

    public ModelCallTimeoutException(String operation, Duration timeout) {
        super("Model " + operation + " call exceeded timeout " + timeout);
        this.operation = operation;
        this.timeout = timeout;
    }

    public String operation() {
        return operation;
    }

    public Duration timeout() {
        return timeout;
    }
}
