package com.passage.agent.agent.policy;

import com.passage.agent.agent.tool.ToolCallRequest;
import com.passage.agent.agent.tool.ToolCallResult;
import com.passage.agent.agent.tool.ToolId;
import com.passage.agent.agent.tool.ToolRegistry;
import com.passage.agent.agent.tool.web.WebReaderToolAdapter;
import com.passage.agent.agent.tool.web.WebResponse;
import com.passage.agent.agent.tool.web.WebTransport;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolPolicyGatewayContractTest {
    private static final ToolPolicy POLICY = new ToolPolicy(Duration.ofSeconds(1), Duration.ofSeconds(1), 1_000, 80, 3, 1, 512);

    @Test
    void rejectsUnauthorizedAndUnregisteredToolsBeforeTransportAndRedactsAuditTarget() {
        List<ToolCallAuditEvent> audits = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        ToolPolicyGateway gateway = gateway(publicResolver(), (uri, connectTimeout, readTimeout, maxResponseBytes) -> {
            calls.incrementAndGet();
            return html("<p>safe</p>");
        }, audits);

        assertPolicyError(ToolPolicyError.UNAUTHORIZED_TOOL, () -> gateway.execute(request(ToolId.WEB_READER, Set.of(ToolId.SEARCH))));
        assertPolicyError(ToolPolicyError.UNKNOWN_TOOL, () -> gateway.execute(request(ToolId.SEARCH, Set.of(ToolId.SEARCH))));

        assertEquals(0, calls.get());
        assertEquals(2, audits.size());
        assertFalse(audits.getFirst().target().contains("secret"));
        assertEquals(ToolPolicyError.UNAUTHORIZED_TOOL, audits.getFirst().error());
    }

    @Test
    void blocksUnsafeProtocolAndRedirectBeforeSecondRequest() {
        List<ToolCallAuditEvent> audits = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        HostResolver resolver = host -> switch (host) {
            case "public.example" -> new InetAddress[]{InetAddress.getByName("8.8.8.8")};
            case "private.example" -> new InetAddress[]{InetAddress.getByName("10.0.0.1")};
            default -> throw new java.net.UnknownHostException(host);
        };
        ToolPolicyGateway gateway = gateway(resolver, (uri, connectTimeout, readTimeout, maxResponseBytes) -> {
            calls.incrementAndGet();
            return new WebResponse(302, "text/html", new byte[0], "https://private.example/internal");
        }, audits);

        assertPolicyError(ToolPolicyError.UNSAFE_URL, () -> gateway.execute(new ToolCallRequest("run-1", ToolId.WEB_READER,
                "http://public.example/path", Set.of(ToolId.WEB_READER), 1)));
        assertPolicyError(ToolPolicyError.UNSAFE_NETWORK_ADDRESS, () -> gateway.execute(request(ToolId.WEB_READER, Set.of(ToolId.WEB_READER))));

        assertEquals(1, calls.get());
        assertEquals(ToolPolicyError.UNSAFE_NETWORK_ADDRESS, audits.getLast().error());
    }

    @Test
    void normalizesHtmlAndRetriesOnlyRetryableServerResponse() {
        List<ToolCallAuditEvent> audits = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        ToolPolicyGateway gateway = gateway(publicResolver(), (uri, connectTimeout, readTimeout, maxResponseBytes) -> {
            if (calls.incrementAndGet() == 1) return new WebResponse(503, "text/html", new byte[0], null);
            return html("<html><head><title>Safe title</title><style>hidden</style><script>ignore()</script></head>"
                    + "<body>Untrusted page text<form>do not submit</form></body></html>");
        }, audits);

        ToolCallResult result = gateway.execute(request(ToolId.WEB_READER, Set.of(ToolId.WEB_READER)));

        assertEquals(2, calls.get());
        assertEquals("Safe title", result.sources().getFirst().title());
        assertTrue(result.sources().getFirst().summary().contains("Untrusted page text"));
        assertFalse(result.sources().getFirst().summary().contains("ignore"));
        assertFalse(result.sources().getFirst().summary().contains("do not submit"));
        assertEquals(1, result.retries());
        assertTrue(audits.getFirst().successful());
        assertEquals(1, audits.getFirst().retries());
    }

    @Test
    void rejectsOversizedAndNonTextResponsesWithoutRetries() {
        List<ToolCallAuditEvent> audits = new ArrayList<>();
        ToolPolicyGateway oversized = gateway(publicResolver(), (uri, connectTimeout, readTimeout, maxResponseBytes) -> new WebResponse(200, "text/plain", new byte[1_001], null), audits);
        assertPolicyError(ToolPolicyError.RESPONSE_TOO_LARGE,
                () -> oversized.execute(request(ToolId.WEB_READER, Set.of(ToolId.WEB_READER))));

        ToolPolicyGateway binary = gateway(publicResolver(), (uri, connectTimeout, readTimeout, maxResponseBytes) -> new WebResponse(200, "application/pdf", "x".getBytes(), null), audits);
        assertPolicyError(ToolPolicyError.UNSUPPORTED_CONTENT_TYPE,
                () -> binary.execute(request(ToolId.WEB_READER, Set.of(ToolId.WEB_READER))));
    }

    private static ToolPolicyGateway gateway(HostResolver resolver, WebTransport transport, List<ToolCallAuditEvent> audits) {
        WebReaderToolAdapter adapter = new WebReaderToolAdapter(new UrlSafetyValidator(resolver, POLICY.maxUrlLength()), transport);
        return new ToolPolicyGateway(new ToolRegistry(List.of(adapter)), POLICY, audits::add);
    }

    private static ToolCallRequest request(ToolId toolId, Set<ToolId> allowedTools) {
        return new ToolCallRequest("run-1", toolId, "https://public.example/path?token=secret", allowedTools, 1);
    }

    private static HostResolver publicResolver() {
        return host -> new InetAddress[]{InetAddress.getByName("8.8.8.8")};
    }

    private static WebResponse html(String html) {
        return new WebResponse(200, "text/html; charset=utf-8", html.getBytes(), null);
    }

    private static void assertPolicyError(ToolPolicyError expected, Runnable action) {
        ToolPolicyException exception = assertThrows(ToolPolicyException.class, action::run);
        assertEquals(expected, exception.error());
    }
}
