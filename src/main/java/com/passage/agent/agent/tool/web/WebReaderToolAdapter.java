package com.passage.agent.agent.tool.web;

import com.passage.agent.agent.policy.ToolPolicy;
import com.passage.agent.agent.policy.ToolPolicyError;
import com.passage.agent.agent.policy.ToolPolicyException;
import com.passage.agent.agent.policy.UrlSafetyValidator;
import com.passage.agent.agent.tool.ToolAdapter;
import com.passage.agent.agent.tool.ToolCallRequest;
import com.passage.agent.agent.tool.ToolCallResult;
import com.passage.agent.agent.tool.ToolId;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Reads already-authorized public HTTPS pages, manually checking every redirect target. */
public final class WebReaderToolAdapter implements ToolAdapter {
    private final UrlSafetyValidator urlSafetyValidator;
    private final WebTransport transport;

    public WebReaderToolAdapter(UrlSafetyValidator urlSafetyValidator, WebTransport transport) {
        this.urlSafetyValidator = urlSafetyValidator;
        this.transport = transport;
    }

    @Override
    public ToolId id() {
        return ToolId.WEB_READER;
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request, ToolPolicy policy) {
        Instant started = Instant.now();
        URI target = parse(request.input());
        int redirects = 0;
        while (true) {
            urlSafetyValidator.validate(target);
            WebFetchOutcome outcome = fetchWithRetry(target, policy);
            WebResponse response = outcome.response();
            if (response.isRedirect()) {
                if (redirects++ >= policy.maxRedirects() || response.redirectLocation() == null || response.redirectLocation().isBlank()) {
                    throw new ToolPolicyException(ToolPolicyError.REDIRECT_LIMIT, "Redirect is not permitted");
                }
                target = target.resolve(response.redirectLocation());
                continue;
            }
            if (response.statusCode() >= 400 && response.statusCode() < 500) {
                throw new ToolPolicyException(ToolPolicyError.HTTP_CLIENT_ERROR, "Web reader received a client error");
            }
            if (response.statusCode() >= 500) {
                throw new ToolPolicyException(ToolPolicyError.HTTP_SERVER_ERROR, "Web reader received a server error");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ToolPolicyException(ToolPolicyError.TRANSPORT_FAILURE, "Web reader received an invalid response");
            }
            return toResult(target, response, policy, Duration.between(started, Instant.now()), outcome.retries());
        }
    }

    private WebFetchOutcome fetchWithRetry(URI target, ToolPolicy policy) {
        ToolPolicyException lastFailure = null;
        for (int attempt = 0; attempt <= policy.maxRetries(); attempt++) {
            try {
                urlSafetyValidator.validate(target);
                WebResponse response = transport.fetch(target, policy.connectTimeout(), policy.readTimeout(), policy.maxResponseBytes());
                if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    lastFailure = new ToolPolicyException(ToolPolicyError.HTTP_SERVER_ERROR, "Web reader retryable response");
                    if (attempt < policy.maxRetries()) continue;
                    throw lastFailure;
                }
                return new WebFetchOutcome(response, attempt);
            } catch (ToolPolicyException e) {
                throw e;
            } catch (RuntimeException e) {
                lastFailure = new ToolPolicyException(ToolPolicyError.TRANSPORT_FAILURE, "Web reader transport failed", e);
                if (attempt == policy.maxRetries()) throw lastFailure;
            }
        }
        throw lastFailure;
    }

    private ToolCallResult toResult(URI target, WebResponse response, ToolPolicy policy, Duration elapsed, int retries) {
        byte[] body = response.body();
        if (body.length > policy.maxResponseBytes()) {
            throw new ToolPolicyException(ToolPolicyError.RESPONSE_TOO_LARGE, "Web reader response exceeded byte limit");
        }
        String contentType = response.contentType() == null ? "" : response.contentType().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("text/html") && !contentType.startsWith("text/plain")) {
            throw new ToolPolicyException(ToolPolicyError.UNSUPPORTED_CONTENT_TYPE, "Web reader response type is not text");
        }
        String rawText = new String(body, StandardCharsets.UTF_8);
        String title;
        String visibleText;
        if (contentType.startsWith("text/html")) {
            Document document = Jsoup.parse(rawText, target.toString());
            document.select("script,style,noscript,form,template").remove();
            title = document.title().isBlank() ? "Untitled" : document.title();
            visibleText = document.body() == null ? "" : document.body().text();
        } else {
            title = "Untitled";
            visibleText = rawText;
        }
        if (visibleText.isBlank()) {
            throw new ToolPolicyException(ToolPolicyError.TRANSPORT_FAILURE, "Web reader response did not contain readable text");
        }
        if (visibleText.length() > policy.maxTextCharacters()) {
            throw new ToolPolicyException(ToolPolicyError.RESPONSE_TEXT_TOO_LARGE, "Web reader response exceeded text limit");
        }
        ToolCallResult.SourceCandidate source = new ToolCallResult.SourceCandidate(target.toString(), title, null,
                visibleText, sha256(body));
        return new ToolCallResult(ToolId.WEB_READER, List.of(source), elapsed, retries, body.length);
    }

    private static URI parse(String input) {
        try {
            return URI.create(input);
        } catch (IllegalArgumentException e) {
            throw new ToolPolicyException(ToolPolicyError.UNSAFE_URL, "Web reader URL is invalid", e);
        }
    }

    private static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder value = new StringBuilder();
            for (byte b : digest) value.append(String.format("%02x", b));
            return value.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 must be available", e);
        }
    }

    private record WebFetchOutcome(WebResponse response, int retries) { }
}
