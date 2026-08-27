package com.yupi.template.agent.state;

/**
 * Legacy StateGraph map keys for the article workflow.
 *
 * <p>The graph framework still transports a {@code Map<String, Object>} during
 * the transition to {@link WorkflowState}. Keeping the vocabulary here makes
 * that framework boundary explicit and prevents node-local string literals
 * from silently diverging.</p>
 */
public final class ArticleWorkflowKeys {

    public static final String TASK_ID = "taskId";
    public static final String TOPIC = "topic";
    public static final String STYLE = "style";
    public static final String USER_DESCRIPTION = "userDescription";
    public static final String MAIN_TITLE = "mainTitle";
    public static final String SUB_TITLE = "subTitle";
    public static final String TITLE_OPTIONS = "titleOptions";
    public static final String OUTLINE = "outline";
    public static final String CONTENT = "content";
    public static final String CONTENT_WITH_PLACEHOLDERS = "contentWithPlaceholders";
    public static final String IMAGE_REQUIREMENTS = "imageRequirements";
    public static final String IMAGES = "images";
    public static final String FULL_CONTENT = "fullContent";
    public static final String ENABLED_IMAGE_METHODS = "enabledImageMethods";

    private ArticleWorkflowKeys() {
    }
}
