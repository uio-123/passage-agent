package com.passage.agent.agent.llm;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the actual Spring AI OpenAI HTTP and SSE codecs against a local server. */
@Timeout(30)
class SpringAiModelAdapterHttpTest {

    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void adaptsRegularOpenAiCompatibleResponse() {
        server.enqueue(jsonResponse("""
                {"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"regular"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """));

        withModelPort(model -> assertThat(model.complete("hello")).isEqualTo("regular"));
    }

    @Test
    void adaptsSseChunksInOrder() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("""
                        data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[{"index":0,"delta":{"role":"assistant","content":"first "},"finish_reason":null}]}

                        data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[{"index":0,"delta":{"content":"second"},"finish_reason":"stop"}]}

                        data: [DONE]

                        """));

        withModelPort(model -> assertThat(model.stream("hello").collectList().block())
                .containsExactly("first ", "second"));
    }

    @Test
    void exposesOpenAiCompatibleErrorToCaller() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("""
                {"error":{"message":"invalid request","type":"invalid_request_error","code":"invalid_request"}}
                """));

        withModelPort(model -> assertThatThrownBy(() -> model.complete("hello"))
                .isInstanceOf(RuntimeException.class));
    }

    @Test
    void parsesStructuredJsonWrappedInMarkdownCodeFenceAndReasoningPrefix() {
        server.enqueue(jsonResponse("""
                {"id":"chatcmpl-1","object":"chat.completion","created":1,"model":"test-model",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"thinking...\\n```json\\n{\\"score\\":92}\\n```"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """));

        withModelPort(model -> assertThat(model.<Map<String, Integer>>completeStructured(
                "return json", new TypeToken<Map<String, Integer>>() { }.getType()))
                .containsEntry("score", 92));
    }

    private void withModelPort(java.util.function.Consumer<AiModelPort> assertion) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        RestClientAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class,
                        OpenAiChatAutoConfiguration.class
                ))
                .withUserConfiguration(ModelPortConfiguration.class)
                .withBean(ResponseErrorHandler.class, DefaultResponseErrorHandler::new)
                .withBean(RetryTemplate.class, SpringAiModelAdapterHttpTest::noRetryTemplate)
                .withPropertyValues(
                        "spring.ai.openai.base-url=" + server.url("/").toString(),
                        "spring.ai.openai.api-key=test-key",
                        "spring.ai.openai.chat.options.model=test-model"
                )
                .run(context -> assertion.accept(context.getBean(AiModelPort.class)));
    }

    private static RetryTemplate noRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(1));
        return retryTemplate;
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ModelPortConfiguration {
        @Bean
        AiModelPort aiModelPort(ChatModel chatModel) {
            return new SpringAiModelAdapter(chatModel);
        }
    }
}
