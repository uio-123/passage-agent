package com.passage.agent.agent.policy;

import com.passage.agent.agent.tool.ToolAdapter;
import com.passage.agent.agent.tool.ToolAuthorization;
import com.passage.agent.agent.tool.ToolCallRequest;
import com.passage.agent.agent.tool.ToolCallResult;
import com.passage.agent.agent.tool.ToolRegistry;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;

/** The sole application-facing executor for P2 research tools. */
public final class ToolPolicyGateway {
    private final ToolRegistry registry;
    private final ToolPolicy policy;
    private final ToolAuditSink auditSink;

    public ToolPolicyGateway(ToolRegistry registry, ToolPolicy policy, ToolAuditSink auditSink) {
        this.registry = registry;
        this.policy = policy;
        this.auditSink = auditSink;
    }

    public ToolCallResult execute(ToolCallRequest request) {
        Instant startedAt = Instant.now();
        String target = redactTarget(request.input());
        int retries = 0;
        int responseBytes = 0;
        try {
            try {
                ToolAuthorization.requireAllowed(request);
            } catch (IllegalArgumentException e) {
                throw new ToolPolicyException(ToolPolicyError.UNAUTHORIZED_TOOL, "Tool is not authorized");
            }
            ToolAdapter adapter;
            try {
                adapter = registry.required(request.toolId());
            } catch (IllegalArgumentException e) {
                throw new ToolPolicyException(ToolPolicyError.UNKNOWN_TOOL, "Tool is not registered");
            }
            ToolCallResult result = adapter.execute(request, policy);
            retries = result.retries();
            responseBytes = result.responseBytes();
            auditSink.record(new ToolCallAuditEvent(request.runId(), request.toolId(), target, true, null,
                    Duration.between(startedAt, Instant.now()), retries, responseBytes));
            return result;
        } catch (ToolPolicyException e) {
            auditSink.record(new ToolCallAuditEvent(request.runId(), request.toolId(), target, false, e.error(),
                    Duration.between(startedAt, Instant.now()), retries, responseBytes));
            throw e;
        } catch (RuntimeException e) {
            auditSink.record(new ToolCallAuditEvent(request.runId(), request.toolId(), target, false,
                    ToolPolicyError.TRANSPORT_FAILURE, Duration.between(startedAt, Instant.now()), retries, responseBytes));
            throw new ToolPolicyException(ToolPolicyError.TRANSPORT_FAILURE, "Tool execution failed", e);
        }
    }

    private static String redactTarget(String input) {
        try {
            URI uri = URI.create(input);
            String origin = uri.getScheme() == null || uri.getHost() == null ? "invalid" : uri.getScheme() + "://" + uri.getHost();
            return origin + "/" + sha256(uri.getRawPath() == null ? "" : uri.getRawPath());
        } catch (IllegalArgumentException ignored) {
            return "invalid";
        }
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format("%02x", b));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available", e);
        }
    }
}
