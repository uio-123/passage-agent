package com.yupi.template.agent.llm;

import com.yupi.template.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.lang.reflect.Type;

/** Adapts Spring AI to the narrow model port used by this application. */
@Component
@RequiredArgsConstructor
public class SpringAiModelAdapter implements AiModelPort {

    private final ChatModel chatModel;

    @Override
    public String complete(String prompt) {
        return requireText(chatModel.call(new Prompt(new UserMessage(prompt))).getResult().getOutput().getText());
    }

    @Override
    public Flux<String> stream(String prompt) {
        return chatModel.stream(new Prompt(new UserMessage(prompt)))
                .map(response -> response.getResult().getOutput().getText())
                .filter(chunk -> chunk != null && !chunk.isEmpty());
    }

    @Override
    public <T> T completeStructured(String prompt, Type responseType) {
        T result = GsonUtils.fromJson(complete(prompt), responseType);
        if (result == null) {
            throw new IllegalStateException("模型未返回可解析的结构化结果");
        }
        return result;
    }

    private String requireText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("模型未返回文本内容");
        }
        return text;
    }
}
