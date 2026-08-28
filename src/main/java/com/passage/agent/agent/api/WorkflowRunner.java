package com.passage.agent.agent.api;

import com.passage.agent.agent.state.WorkflowState;

import java.util.function.Consumer;

/** Application-facing workflow boundary; callers do not depend on StateGraph types. */
public interface WorkflowRunner {

    WorkflowExecutionResult generateTitles(WorkflowState state, Consumer<String> streamHandler);

    WorkflowExecutionResult generateOutline(WorkflowState state, Consumer<String> streamHandler);

    WorkflowExecutionResult generateContent(WorkflowState state, Consumer<String> streamHandler);
}
