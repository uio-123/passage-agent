package com.passage.agent.agent.agents;

import com.passage.agent.agent.llm.AiModelPort;
import com.passage.agent.agent.state.ArticleWorkflowKeys;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.google.gson.reflect.TypeToken;
import com.passage.agent.constant.PromptConstant;
import com.passage.agent.model.dto.article.ArticleState;
import com.passage.agent.model.enums.ArticleStyleEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 标题生成 Agent
 * 根据选题生成 3-5 个爆款标题方案
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TitleGeneratorAgent implements NodeAction {

    private final AiModelPort aiModel;

    public static final String INPUT_TOPIC = ArticleWorkflowKeys.TOPIC;
    public static final String INPUT_STYLE = ArticleWorkflowKeys.STYLE;
    public static final String OUTPUT_TITLE_OPTIONS = ArticleWorkflowKeys.TITLE_OPTIONS;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String topic = state.value(INPUT_TOPIC)
                .map(Object::toString)
                .orElseThrow(() -> new IllegalArgumentException("缺少选题参数"));
        
        String style = state.value(INPUT_STYLE)
                .map(Object::toString)
                .orElse(null);
        
        log.info("TitleGeneratorAgent 开始执行: topic={}, style={}", topic, style);
        
        // 构建 prompt
        String prompt = PromptConstant.AGENT1_TITLE_PROMPT
                .replace("{topic}", topic)
                + getStylePrompt(style);
        
        // 调用 LLM
        List<ArticleState.TitleOption> titleOptions = aiModel.completeStructured(
                prompt, new TypeToken<List<ArticleState.TitleOption>>() {}.getType());
        
        log.info("TitleGeneratorAgent 执行完成: 生成了 {} 个标题方案", titleOptions.size());
        
        return Map.of(OUTPUT_TITLE_OPTIONS, titleOptions);
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
