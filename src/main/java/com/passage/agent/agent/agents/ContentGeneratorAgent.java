package com.passage.agent.agent.agents;

import com.passage.agent.agent.llm.AiModelPort;
import com.passage.agent.agent.state.ArticleWorkflowKeys;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.passage.agent.agent.context.StreamHandlerContext;
import com.passage.agent.agent.event.AgentStreamEvent;
import com.passage.agent.constant.PromptConstant;
import com.passage.agent.model.dto.article.ArticleState;
import com.passage.agent.model.enums.ArticleStyleEnum;
import com.passage.agent.model.enums.SseMessageTypeEnum;
import com.passage.agent.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 正文生成 Agent
 * 根据大纲生成文章正文内容（支持流式输出）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ContentGeneratorAgent implements NodeAction {

    private final AiModelPort aiModel;

    public static final String INPUT_MAIN_TITLE = ArticleWorkflowKeys.MAIN_TITLE;
    public static final String INPUT_SUB_TITLE = ArticleWorkflowKeys.SUB_TITLE;
    public static final String INPUT_OUTLINE = ArticleWorkflowKeys.OUTLINE;
    public static final String INPUT_STYLE = ArticleWorkflowKeys.STYLE;
    public static final String OUTPUT_CONTENT = ArticleWorkflowKeys.CONTENT;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String mainTitle = state.value(INPUT_MAIN_TITLE)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少主标题参数"));
        
        String subTitle = state.value(INPUT_SUB_TITLE)
                .map(Object::toString)
                .orElse("");
        
        @SuppressWarnings("unchecked")
        ArticleState.OutlineResult outline = state.value(INPUT_OUTLINE)
                .map(v -> {
                    if (v instanceof ArticleState.OutlineResult) {
                        return (ArticleState.OutlineResult) v;
                    }
                    return GsonUtils.fromJson(GsonUtils.toJson(v), ArticleState.OutlineResult.class);
                })
                .orElseThrow(() -> new IllegalArgumentException("缺少大纲参数"));
        
        String style = state.value(INPUT_STYLE)
                .map(Object::toString)
                .orElse(null);
        
        log.info("ContentGeneratorAgent 开始执行: mainTitle={}", mainTitle);
        
        // 构建 prompt
        String outlineText = GsonUtils.toJson(outline.getSections());
        String prompt = PromptConstant.AGENT3_CONTENT_PROMPT
                .replace("{mainTitle}", mainTitle)
                .replace("{subTitle}", subTitle)
                .replace("{outline}", outlineText)
                + getStylePrompt(style);
        
        // 获取流式处理器
        Consumer<AgentStreamEvent> streamHandler = StreamHandlerContext.get();
        
        // 调用 LLM（流式输出）
        String content = callLlmWithStreaming(prompt, streamHandler);
        
        log.info("ContentGeneratorAgent 执行完成: 正文长度={}", content.length());
        
        return Map.of(OUTPUT_CONTENT, content);
    }

    /**
     * 调用 LLM（流式输出）
     */
    private String callLlmWithStreaming(String prompt, Consumer<AgentStreamEvent> streamHandler) {
        StringBuilder contentBuilder = new StringBuilder();
        
        Flux<String> streamResponse = aiModel.stream(prompt);
        
        streamResponse
                .doOnNext(chunk -> {
                    contentBuilder.append(chunk);
                    // 带前缀发送流式消息
                    StreamHandlerContext.send("content_generator", SseMessageTypeEnum.AGENT3_STREAMING, chunk);
                })
                .doOnError(error -> log.error("ContentGeneratorAgent 流式调用失败", error))
                .blockLast();
        
        return contentBuilder.toString();
    }

    /**
     * 根据风格获取对应的 Prompt 附加内容
     */
    private String getStylePrompt(String style) {
        if (style == null || style.isEmpty()) {
            return "";
        }
        
        ArticleStyleEnum styleEnum = ArticleStyleEnum.getEnumByValue(style);
        if (styleEnum == null) {
            return "";
        }
        
        return switch (styleEnum) {
            case TECH -> PromptConstant.STYLE_TECH_PROMPT;
            case EMOTIONAL -> PromptConstant.STYLE_EMOTIONAL_PROMPT;
            case EDUCATIONAL -> PromptConstant.STYLE_EDUCATIONAL_PROMPT;
            case HUMOROUS -> PromptConstant.STYLE_HUMOROUS_PROMPT;
        };
    }
}
