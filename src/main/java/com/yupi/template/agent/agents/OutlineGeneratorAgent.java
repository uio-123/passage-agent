package com.yupi.template.agent.agents;

import com.yupi.template.agent.llm.AiModelPort;
import com.yupi.template.agent.state.ArticleWorkflowKeys;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.yupi.template.agent.context.StreamHandlerContext;
import com.yupi.template.agent.event.AgentStreamEvent;
import com.yupi.template.constant.PromptConstant;
import com.yupi.template.model.dto.article.ArticleState;
import com.yupi.template.model.enums.ArticleStyleEnum;
import com.yupi.template.model.enums.SseMessageTypeEnum;
import com.yupi.template.utils.GsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 大纲生成 Agent
 * 根据标题生成文章大纲（支持流式输出）
 *
 * @author AI Passage Creator
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OutlineGeneratorAgent implements NodeAction {

    private final AiModelPort aiModel;

    public static final String INPUT_MAIN_TITLE = ArticleWorkflowKeys.MAIN_TITLE;
    public static final String INPUT_SUB_TITLE = ArticleWorkflowKeys.SUB_TITLE;
    public static final String INPUT_USER_DESCRIPTION = ArticleWorkflowKeys.USER_DESCRIPTION;
    public static final String INPUT_STYLE = ArticleWorkflowKeys.STYLE;
    public static final String OUTPUT_OUTLINE = ArticleWorkflowKeys.OUTLINE;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String mainTitle = state.value(INPUT_MAIN_TITLE)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少主标题参数"));
        
        String subTitle = state.value(INPUT_SUB_TITLE)
                .map(Object::toString)
                .orElse("");
        
        String userDescription = state.value(INPUT_USER_DESCRIPTION)
                .map(Object::toString)
                .orElse(null);
        
        String style = state.value(INPUT_STYLE)
                .map(Object::toString)
                .orElse(null);
        
        log.info("OutlineGeneratorAgent 开始执行: mainTitle={}, subTitle={}", mainTitle, subTitle);
        
        // 构建用户描述部分
        String descriptionSection = "";
        if (userDescription != null && !userDescription.trim().isEmpty()) {
            descriptionSection = PromptConstant.AGENT2_DESCRIPTION_SECTION
                    .replace("{userDescription}", userDescription);
        }
        
        // 构建 prompt
        String prompt = PromptConstant.AGENT2_OUTLINE_PROMPT
                .replace("{mainTitle}", mainTitle)
                .replace("{subTitle}", subTitle)
                .replace("{descriptionSection}", descriptionSection)
                + getStylePrompt(style);
        
        // 获取流式处理器
        Consumer<AgentStreamEvent> streamHandler = StreamHandlerContext.get();
        
        // 调用 LLM（流式输出）
        String content = callLlmWithStreaming(prompt, streamHandler);
        
        // 解析结果
        ArticleState.OutlineResult outlineResult = GsonUtils.fromJson(
                content, 
                ArticleState.OutlineResult.class
        );
        
        log.info("OutlineGeneratorAgent 执行完成: 生成了 {} 个章节", 
                outlineResult.getSections().size());
        
        return Map.of(OUTPUT_OUTLINE, outlineResult);
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
                    StreamHandlerContext.send("outline_generator", SseMessageTypeEnum.AGENT2_STREAMING, chunk);
                })
                .doOnError(error -> log.error("OutlineGeneratorAgent 流式调用失败", error))
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
