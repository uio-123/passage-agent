package com.passage.agent.agent.tool.web;

import com.passage.agent.agent.policy.ToolPolicyError;
import com.passage.agent.agent.policy.ToolPolicyException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Production-capable transport with redirects disabled; it is not exposed as a Spring bean in P2 E2. */
public final class JdkWebTransport implements WebTransport {
    @Override
    public WebResponse fetch(URI uri, Duration connectTimeout, Duration readTimeout, int maxResponseBytes) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(readTimeout)
                .header("Accept", "text/html, text/plain;q=0.9")
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                return new WebResponse(response.statusCode(), response.headers().firstValue("Content-Type").orElse(""),
                        readBounded(body, maxResponseBytes), response.headers().firstValue("Location").orElse(null));
            }
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ToolPolicyException(ToolPolicyError.TIMEOUT, "Web reader request timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolPolicyException(ToolPolicyError.TIMEOUT, "Web reader request interrupted", e);
        } catch (IOException e) {
            throw new ToolPolicyException(ToolPolicyError.TRANSPORT_FAILURE, "Web reader request failed", e);
        }
    }

    private static byte[] readBounded(InputStream input, int maxResponseBytes) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > maxResponseBytes) {
                throw new ToolPolicyException(ToolPolicyError.RESPONSE_TOO_LARGE, "Web reader response exceeded byte limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
