package com.yupi.template.agent.api;

/** Runtime exception carrying the project-owned error contract. */
public class WorkflowExecutionException extends RuntimeException {

    private final WorkflowError error;

    public WorkflowExecutionException(WorkflowError error, Throwable cause) {
        super(error.message(), cause);
        this.error = error;
    }

    public WorkflowError error() {
        return error;
    }
}
