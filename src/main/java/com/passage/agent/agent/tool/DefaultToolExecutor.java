package com.passage.agent.agent.tool;

import com.passage.agent.agent.policy.ToolPolicyGateway;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Delegates every call through the existing policy, registry and audit gateway. */
@Service
public class DefaultToolExecutor implements ToolExecutor {

    private final ToolPolicyGateway gateway;

    public DefaultToolExecutor(ToolPolicyGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    @Override
    public ToolCallResult execute(ToolContext context, ToolId toolId, String input) {
        Objects.requireNonNull(context, "context");
        return gateway.execute(new ToolCallRequest(
                context.runId(),
                toolId,
                input,
                context.allowedTools(),
                context.remainingCallBudget()));
    }
}
