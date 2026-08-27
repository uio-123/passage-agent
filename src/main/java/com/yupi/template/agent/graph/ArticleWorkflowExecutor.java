package com.yupi.template.agent.graph;

import com.yupi.template.model.dto.article.ArticleState;
import java.util.function.Consumer;

/** Framework-free internal execution boundary used by the workflow runner. */
public interface ArticleWorkflowExecutor {
    void executeTitles(ArticleState state, Consumer<String> streamHandler);
    void executeOutline(ArticleState state, Consumer<String> streamHandler);
    void executeContent(ArticleState state, Consumer<String> streamHandler);
}
