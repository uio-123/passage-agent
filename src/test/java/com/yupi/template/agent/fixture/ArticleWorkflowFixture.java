package com.yupi.template.agent.fixture;

import com.yupi.template.agent.llm.AiModelPort;
import com.yupi.template.agent.run.AgentRun;
import com.yupi.template.agent.state.WorkflowState;
import com.yupi.template.model.dto.article.ArticleState;
import com.yupi.template.utils.GsonUtils;
import reactor.core.publisher.Flux;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Synthetic, network-free inputs shared by article workflow tests. */
public final class ArticleWorkflowFixture {

    private ArticleWorkflowFixture() {
    }

    public static ArticleState legacyState(String taskId) {
        ArticleState state = new ArticleState();
        state.setTaskId(taskId);
        state.setTopic("Java Agent 开发");
        state.setStyle("TECH");
        state.setUserDescription("面向后端初学者");
        return state;
    }

    public static WorkflowState workflowState(String runId, String taskId) {
        return WorkflowState.start(
                AgentRun.root(runId, Instant.parse("2026-08-27T08:00:00Z")), taskId,
                new WorkflowState.ArticleInput("Agent 开发", "TECH", null, List.of()));
    }

    public static List<ArticleState.ImageRequirement> imageRequirementsWithOneFailure() {
        return List.of(imageRequirement(2, "PEXELS"), imageRequirement(1, "MERMAID"), imageRequirement(3, "PEXELS"));
    }

    public static ArticleState.ImageRequirement imageRequirement(int position, String source) {
        ArticleState.ImageRequirement requirement = new ArticleState.ImageRequirement();
        requirement.setPosition(position);
        requirement.setImageSource(source);
        requirement.setKeywords("agent");
        requirement.setType("section");
        requirement.setSectionTitle("section-" + position);
        requirement.setPlaceholderId("{{IMAGE_PLACEHOLDER_" + position + "}}");
        return requirement;
    }

    public static final class DeterministicArticleModel implements AiModelPort {
        private static final String TITLE_OPTIONS = """
                [{"mainTitle":"Agent 开发入门","subTitle":"从工作流到可控协作"}]
                """;
        private static final String IMAGE_ANALYSIS = """
                {"contentWithPlaceholders":"## 什么是 Agent\\n它由模型、状态和工具组成。\\n","imageRequirements":[]}
                """;
        private final List<String> prompts = new ArrayList<>();

        @Override
        public String complete(String prompt) {
            prompts.add(prompt);
            throw new UnsupportedOperationException("The workflow uses structured or streaming calls only");
        }

        @Override
        public Flux<String> stream(String prompt) {
            prompts.add(prompt);
            if (prompt.contains("生成文章大纲")) {
                return Flux.just("{\"sections\":[", "{\"section\":1,\"title\":\"什么是 Agent\",\"points\":[\"目标\",\"状态\"]}]}");
            }
            if (prompt.contains("创作文章正文")) {
                return Flux.just("## 什么是 Agent\n", "它由模型、状态和工具组成。\n");
            }
            return Flux.error(new IllegalArgumentException("Unexpected streaming prompt"));
        }

        @Override
        public <T> T completeStructured(String prompt, Type responseType) {
            prompts.add(prompt);
            String response = responseType.equals(ArticleState.Agent4Result.class) ? IMAGE_ANALYSIS : TITLE_OPTIONS;
            return GsonUtils.fromJson(response, responseType);
        }

        public List<String> prompts() {
            return List.copyOf(prompts);
        }
    }
}
