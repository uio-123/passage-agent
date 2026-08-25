package com.yupi.template;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explicit integration smoke test for a configured LiteLLM proxy.
 * It is excluded from ordinary CI runs and contains no prompts or credentials in output.
 */
@Tag("litellm-smoke")
class LiteLlmSmokeTest {

    private static final String BASE_URL = requiredEnvironment("LITELLM_BASE_URL");
    private static final String API_KEY = requiredEnvironment("LITELLM_API_KEY");
    private static final String MODEL = requiredEnvironment("LITELLM_MODEL");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Test
    void proxySupportsModelsRegularAndStreamingChatCompletions() throws Exception {
        assertSuccess(request("GET", "/v1/models", null));

        String requestBody = """
                {"model":"%s","messages":[{"role":"user","content":"Reply with OK."}],"stream":false}
                """.formatted(jsonEscape(MODEL));
        HttpResponse<String> regularResponse = request("POST", "/v1/chat/completions", requestBody);
        assertSuccess(regularResponse);
        assertThat(regularResponse.body()).contains("choices");

        String streamingRequestBody = requestBody.replace("\"stream\":false", "\"stream\":true");
        HttpResponse<String> streamingResponse = request("POST", "/v1/chat/completions", streamingRequestBody);
        assertSuccess(streamingResponse);
        assertThat(streamingResponse.body()).contains("data:");
    }

    private static HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(trimTrailingSlash(BASE_URL) + path))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json");
        if (body == null) {
            builder.GET();
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertSuccess(HttpResponse<String> response) {
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank() || value.startsWith("your_")) {
            throw new IllegalStateException(name + " must be configured before running the litellm-smoke Maven profile.");
        }
        return value;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
