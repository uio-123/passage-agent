package com.passage.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResponseErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies LiteLLM's OpenAI-compatible configuration without starting business
 * infrastructure such as MySQL, Redis, COS, or an actual LiteLLM endpoint.
 */
class MainApplicationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestClientAutoConfiguration.class,
                    ToolCallingAutoConfiguration.class,
                    OpenAiChatAutoConfiguration.class
            ))
            .withBean(ResponseErrorHandler.class, DefaultResponseErrorHandler::new)
            .withBean(RetryTemplate.class, RetryTemplate::new)
            .withPropertyValues(
                    "spring.ai.openai.base-url=http://127.0.0.1:18080",
                    "spring.ai.openai.api-key=test-key",
                    "spring.ai.openai.chat.options.model=test-model"
            );

    @Test
    void createsOpenAiChatModelFromLiteLlmCompatibleProperties() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(OpenAiChatModel.class));
    }
}
